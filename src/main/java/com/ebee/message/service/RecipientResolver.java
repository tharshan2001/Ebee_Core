package com.ebee.message.service;

import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;

import java.util.List;

public interface RecipientResolver {

    MessageTargetType targetType();

    List<MessageRecipient> resolve(Message message, RecipientResolutionContext context);

    /**
     * Hands the recipients over {@code batchSize} at a time. Resolvers for whole populations
     * (all customers, all agents) override this to read the database a page at a time, so a
     * bulk message never holds every recipient in memory. Selected / manual lists are bounded by
     * the request, so the default (one batch) is fine for them.
     */
    default void resolveInBatches(Message message, RecipientResolutionContext context, int batchSize,
                                  java.util.function.Consumer<List<MessageRecipient>> batch) {
        List<MessageRecipient> all = resolve(message, context);
        for (int i = 0; i < all.size(); i += batchSize) {
            batch.accept(all.subList(i, Math.min(i + batchSize, all.size())));
        }
    }
}
