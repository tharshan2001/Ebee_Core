package com.ebee.message.service;

import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;

import java.util.List;

public interface RecipientResolver {

    MessageTargetType targetType();

    List<MessageRecipient> resolve(Message message, RecipientResolutionContext context);
}
