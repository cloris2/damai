package com.example.service;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.PayDto;
import com.example.dto.TradeCheckDto;
import com.example.entity.PayBill;
import com.example.enums.BaseCode;
import com.example.enums.PayBillStatus;
import com.example.enums.PayChannel;
import com.example.exception.CookFrameException;
import com.example.mapper.PayBillMapper;
import com.example.pay.PayResult;
import com.example.pay.PayStrategyContext;
import com.example.pay.PayStrategyHandler;
import com.example.pay.TradeResult;
import com.example.servicelock.annotion.ServiceLock;
import com.example.util.DateUtils;
import com.example.vo.TradeCheckVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import static com.example.core.DistributedLockConstants.ALIPAY_NOTIFY;
import static com.example.core.DistributedLockConstants.TRADE_CHECK;

/**
 * @program: cook-frame
 * @description:
 * @author: k
 * @create: 2024-01-25
 **/
@Slf4j
@Service
public class PayService {
    
    @Autowired
    private PayBillMapper payBillMapper;
    
    @Autowired
    private PayStrategyContext payStrategyContext;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Transactional(rollbackFor = Exception.class)
    public String commonPay(PayDto payDto) {
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper = 
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, payDto.getOrderNumber());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.nonNull(payBill) && !Objects.equals(payBill.getPayBillStatus(), PayBillStatus.NO_PAY.getCode())) {
            throw new CookFrameException(BaseCode.PAY_BILL_IS_NOT_NO_PAY);
        }
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(payDto.getChannel());
        PayResult pay = payStrategyHandler.pay(String.valueOf(payDto.getOrderNumber()), payDto.getPrice(), 
                payDto.getSubject(),payDto.getNotifyUrl(),payDto.getReturnUrl());
        if (pay.isSuccess()) {
            payBill = new PayBill();
            payBill.setId(uidGenerator.getUID());
            payBill.setOrderNumber(Long.parseLong(payDto.getOrderNumber()));
            payBill.setOutOrderNo(String.valueOf(payDto.getOrderNumber()));
            payBill.setPayChannel(payDto.getChannel());
            payBill.setPayScene("生产");
            payBill.setSubject(payDto.getSubject());
            payBill.setPayAmount(payDto.getPrice());
            payBill.setPayBillType(payDto.getPayBillType());
            payBill.setPayBillStatus(PayBillStatus.NO_PAY.getCode());
            payBill.setPayTime(DateUtils.now());
            payBillMapper.insert(payBill);
        }
        
        return pay.getBody();
    }
    
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(name = ALIPAY_NOTIFY,keys = {"#outTradeNo"})
    public String alipayNotify(Map<String, String> params,String outTradeNo){
        log.info("支付宝回调通知参数 ===> {}", JSON.toJSONString(params));
        String failure = "failure";
        String success = "success";
        //验签
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(PayChannel.ALIPAY.getValue());
        boolean signVerifyResult = payStrategyHandler.signVerify(params);
        if (!signVerifyResult) {
            return failure;
        }
        //按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验
        //1 商户需要验证该通知数据中的 out_trade_no 是否为商户系统中创建的订单号
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, outTradeNo);
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.isNull(payBill)) {
            log.error("账单为空 params : {}",JSON.toJSONString(params));
            return failure;
        }
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            log.info("账单已支付 params : {}",JSON.toJSONString(params));
            return success;
        }
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.CANCEL.getCode())) {
            log.info("账单已取消 params : {}",JSON.toJSONString(params));
            return success;
        }
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.REFUND.getCode())) {
            log.info("账单已退单 params : {}",JSON.toJSONString(params));
            return success;
        }
        //2 判断 total_amount 是否确实为该订单的实际金额（即商户订单创建时的金额）
        BigDecimal notifyPayAmount = new BigDecimal(params.get("total_amount"));
        BigDecimal payAmount = payBill.getPayAmount();
        if (notifyPayAmount.compareTo(payAmount) != 0) {
            log.error("回调金额和账单支付金额不一致 回调金额 : {}, 账单支付金额 : {}",notifyPayAmount,payAmount);
            return failure;
        }
        //3 校验通知中的 seller_id（或者 seller_email) 是否为 out_trade_no 这笔单据的对应的操作方
        String notifySellerId = params.get("seller_id");
        String alipaySellerId = payStrategyHandler.getSellerId();
        if (!notifySellerId.equals(alipaySellerId)) {
            log.error("回调商户pid和已配置商户pid不一致 回调商户pid : {}, 已配置商户pid : {}",notifySellerId,alipaySellerId);
            return failure;
        }
        //4 验证 app_id 是否为该商户本身
        String notifyAppId = params.get("app_id");
        String alipayAppId = payStrategyHandler.getAppId();
        if(!notifyAppId.equals(alipayAppId)){
            log.error("回调appId和已配置appId不一致 回调appId : {}, 已配置appId : {}",notifyAppId,alipayAppId);
            return failure;
        }
        //在支付宝的业务通知中，只有交易通知状态为 TRADE_SUCCESS时，支付宝才会认定为买家付款成功
        String tradeStatus = params.get("trade_status");
        if(!"TRADE_SUCCESS".equals(tradeStatus)){
            log.error("支付未成功 tradeStatus : {}",tradeStatus);
            return failure;
        }
        PayBill updatePayBill = new PayBill();
        updatePayBill.setPayBillStatus(PayBillStatus.PAY.getCode());
        
        LambdaUpdateWrapper<PayBill> payBillLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(PayBill.class).eq(PayBill::getOutOrderNo, outTradeNo);
        
        payBillMapper.update(updatePayBill,payBillLambdaUpdateWrapper);
        return success;
    }
    
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(name = TRADE_CHECK,keys = {"#tradeCheckDto.outTradeNo"})
    public TradeCheckVo tradeCheck(TradeCheckDto tradeCheckDto) {
        TradeCheckVo tradeCheckVo = new TradeCheckVo();
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(tradeCheckDto.getChannel());
        TradeResult tradeResult = payStrategyHandler.queryTrade(tradeCheckDto.getOutTradeNo());
        BeanUtil.copyProperties(tradeResult,tradeCheckVo);
        if (!tradeResult.isSuccess()) {
            return tradeCheckVo;
        }
        BigDecimal totalAmount = tradeResult.getTotalAmount();
        String outTradeNo = tradeResult.getOutTradeNo();
        Integer payBillStatus = tradeResult.getPayBillStatus();
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper = 
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, outTradeNo);
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.isNull(payBill)) {
            log.error("账单为空 tradeCheckDto : {}",JSON.toJSONString(tradeCheckDto));
            return tradeCheckVo;
        }
        if (payBill.getPayAmount().compareTo(totalAmount) != 0) {
            log.error("支付宝和库中账单支付金额不一致 支付宝支付金额 : {}, 库中账单支付金额 : {}, tradeCheckDto : {}",
                    totalAmount,payBill.getPayAmount(),JSON.toJSONString(tradeCheckDto));
            return tradeCheckVo;
        }
        if (!Objects.equals(payBill.getPayBillStatus(), payBillStatus)) {
            log.info("支付宝和库中账单交易状态不一致 支付宝payBillStatus : {}, 库中payBillStatus : {}, tradeCheckDto : {}",
                    payBillStatus,payBill.getPayBillStatus(),JSON.toJSONString(tradeCheckDto));
            PayBill updatePayBill = new PayBill();
            updatePayBill.setId(payBill.getId());
            updatePayBill.setPayBillStatus(payBillStatus);
            payBillMapper.updateById(updatePayBill);
            return tradeCheckVo;
        }
        return tradeCheckVo;
    }
}
