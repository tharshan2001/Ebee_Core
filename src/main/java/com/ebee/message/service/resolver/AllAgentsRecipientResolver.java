package com.ebee.message.service.resolver;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;
import com.ebee.message.enums.RecipientType;
import com.ebee.message.service.RecipientResolutionContext;
import com.ebee.message.service.RecipientResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AllAgentsRecipientResolver implements RecipientResolver {

    private final AgentRepository agentRepository;

    @Override
    public MessageTargetType targetType() {
        return MessageTargetType.ALL_AGENTS;
    }

    @Override
    public List<MessageRecipient> resolve(Message message, RecipientResolutionContext context) {
        return agentRepository.findAllWithUser().stream()
                .map(agent -> buildRecipient(message, agent))
                .toList();
    }

    @Override
    public void resolveInBatches(Message message, RecipientResolutionContext context, int batchSize,
                                 java.util.function.Consumer<List<MessageRecipient>> batch) {
        // A page at a time, in id order so pages don't shift while we read them.
        for (int pageNo = 0; ; pageNo++) {
            var page = org.springframework.data.domain.PageRequest.of(pageNo, batchSize,
                    org.springframework.data.domain.Sort.by("id"));
            var rows = agentRepository.findAllWithUser(page).getContent();
            if (!rows.isEmpty()) {
                batch.accept(rows.stream().map(agent -> buildRecipient(message, agent)).toList());
            }
            if (rows.size() < batchSize) {
                return;
            }
        }
    }

    private MessageRecipient buildRecipient(Message message, Agent agent) {
        return MessageRecipient.builder()
                .message(message)
                .recipientType(RecipientType.AGENT)
                .recipientId(agent.getId())
                .name(agent.getUser().getFirstName() + " " + agent.getUser().getLastName())
                .phoneNumber(agent.getPhoneNumber())
                .email(agent.getUser().getEmail())
                .build();
    }
}
