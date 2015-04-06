/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ctrip.infosec.rule.rabbitmq;

import com.ctrip.infosec.common.Constants;
import com.ctrip.infosec.common.model.RiskFact;
import static com.ctrip.infosec.configs.utils.Utils.JSON;
import com.ctrip.infosec.rule.Contexts;
import com.ctrip.infosec.rule.executor.PostRulesExecutorService;
import com.ctrip.infosec.rule.executor.PreRulesExecutorService;
import com.ctrip.infosec.rule.executor.EventDataMergeService;
import com.ctrip.infosec.rule.executor.RulesExecutorService;
import com.ctrip.infosec.sars.monitor.SarsMonitorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author zhengby
 */
public class RabbitMqMessageHandler {

    private static Logger logger = LoggerFactory.getLogger(RabbitMqMessageHandler.class);

    @Autowired
    private RulesExecutorService rulesExecutorService;
    @Autowired
    private PreRulesExecutorService preRulesExecutorService;
    @Autowired
    private PostRulesExecutorService postRulesExecutorService;
    @Autowired
    private RabbitMqMessageSender rabbitMqMessageSender;
    @Autowired
    private EventDataMergeService eventDataMergeService;

    public void handleMessage(Object message) throws Exception {
        RiskFact fact = null;
        String factTxt = null;
        try {

            if (message instanceof byte[]) {
                factTxt = new String((byte[]) message, Constants.defaultCharset);
            } else if (message instanceof String) {
                factTxt = (String) message;
            } else {
                throw new IllegalArgumentException("消息格式只支持\"String\"或\"byte[]\"");
            }

            logger.info("MQ: fact=" + factTxt);
            fact = JSON.parseObject((String) factTxt, RiskFact.class);
            Contexts.setLogPrefix("[" + fact.eventPoint + "][" + fact.eventId + "] ");
            SarsMonitorContext.setLogPrefix(Contexts.getLogPrefix());

            //执行订单合并
            eventDataMergeService.executeRedisOption(fact);
            // 执行预处理
            preRulesExecutorService.executePreRules(fact, true);
            // 执行异步规则
            rulesExecutorService.executeAsyncRules(fact);
            // 执行后处理
            postRulesExecutorService.executePostRules(fact, true);
        } catch (Throwable ex) {
            logger.error(Contexts.getLogPrefix() + "invoke query exception.", ex);
        } finally {
            // 发送给DataDispatcher
            if (fact != null) {
                rabbitMqMessageSender.sendToDataDispatcher(fact);
            }
        }
    }
}