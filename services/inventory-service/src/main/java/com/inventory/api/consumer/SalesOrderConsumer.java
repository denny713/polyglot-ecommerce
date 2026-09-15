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

/**
 * The messaging entry point of the sales order flow.
 * <p>
 * A sales order is created and paid for in the order service; this service only
 * hears about it once, when the payment settles, and answers by moving stock. That
 * is why there is no controller for it: the caller is a broker, not a user, and
 * nothing here is triggered by an HTTP request.
 * <p>
 * The listener deliberately holds no logic of its own beyond deciding what a
 * failure means. Everything it does is delegated to
 * {@link com.inventory.api.service.SalesOrderService#doSubmit}, which runs in its
 * own transaction, so a message is either fully applied or fully rolled back.
 * <p>
 * Failures are split in two, because the broker treats them differently:
 * <ul>
 *   <li>A message this service will never be able to apply — no id, an order that
 *       does not exist, or a line asking for more than the stock holds — is
 *       rejected with {@link AmqpRejectAndDontRequeueException}. Redelivering it
 *       would fail identically, so it goes straight to the dead letter queue.</li>
 *   <li>Anything else is allowed to propagate. The container rejects it with
 *       {@code default-requeue-rejected} false, so it also reaches the dead letter
 *       queue rather than looping — but it is there to be replayed once the cause
 *       is fixed, not to be discarded.</li>
 * </ul>
 * <p>
 * Note the {@link AccountUtil} bracket. Auditing in {@code Base} reads the user
 * from a thread local that {@code TokenFilter} populates per request, and a
 * listener thread never passes through that filter. Without the header the stock
 * rows this consumer writes would record no author at all, and without the
 * {@code finally} the pooled listener thread would carry one message's user into
 * the next one.
 */
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
