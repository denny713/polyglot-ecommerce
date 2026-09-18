package com.inventory.api.consumer;

import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.service.SalesOrderService;
import com.inventory.api.util.AccountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The messaging entry point of the sales order flow. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderConsumer {

    /**
     * Carries the id of the user whose payment produced the message. Optional: a
     * message sent by a scheduler rather than by a person has no user behind it.
     */
    public static final String USER_HEADER = "X-User-Id";

    private final SalesOrderService soService;

    @RabbitListener(queues = "${rabbitmq.queue.so-submit}")
    public void doConsumeSubmit(SOSubmitReq req,
                                @Header(name = USER_HEADER, required = false) String userId) {
        if (req == null || req.getId() == null) {
            throw new AmqpRejectAndDontRequeueException("Sales order message without an id");
        }

        log.info("Received sales order submit message for id {}", req.getId());
        AccountUtil.setUserLogin(resolveUserLogin(userId));

        try {
            soService.doSubmit(req);
            log.info("Sales order {} submitted", req.getId());
        } catch (BadRequestException | NotFoundException e) {
            log.error("Rejecting sales order {}: {}", req.getId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } finally {
            AccountUtil.clearUserLogin();
        }
    }

    /**
     * A header that is absent or not an uuid leaves the audit columns empty rather
     * than failing the message: the stock movement matters more than knowing who
     * triggered it.
     */
    private UUID resolveUserLogin(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring malformed {} header {}", USER_HEADER, userId);
            return null;
        }
    }
}
