package com.ebee.agent.service;

import com.ebee.agent.entity.Agent;
import com.ebee.common.port.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AgentDocumentService {

    private final StoragePort storagePort;

    public void uploadNewDocuments(Agent agent, MultipartFile nicDocument, MultipartFile bankPassbookDocument) {
        if (nicDocument != null && !nicDocument.isEmpty()) {
            agent.setNicDocumentUrl(storagePort.uploadFile(nicDocument, "agents/nic", agent.getAgentCode() + "_NIC"));
            agent.setNicDocumentName(nicDocument.getOriginalFilename());
            agent.setNicDocumentType(nicDocument.getContentType());
        }

        if (bankPassbookDocument != null && !bankPassbookDocument.isEmpty()) {
            agent.setBankPassbookDocumentUrl(storagePort.uploadFile(bankPassbookDocument, "agents/passbook", agent.getAgentCode() + "_Passbook"));
            agent.setBankPassbookDocumentName(bankPassbookDocument.getOriginalFilename());
            agent.setBankPassbookDocumentType(bankPassbookDocument.getContentType());
        }
    }

    public void replaceDocuments(Agent agent, MultipartFile nicDocument, MultipartFile bankPassbookDocument) {
        boolean hasNicDoc = nicDocument != null && !nicDocument.isEmpty();
        boolean hasPassbookDoc = bankPassbookDocument != null && !bankPassbookDocument.isEmpty();

        if (hasNicDoc) {
            if (agent.getNicDocumentUrl() != null) {
                storagePort.deleteFile(agent.getNicDocumentUrl());
            }
            agent.setNicDocumentUrl(storagePort.uploadFile(nicDocument, "agents/nic", agent.getAgentCode() + "_NIC"));
            agent.setNicDocumentName(nicDocument.getOriginalFilename());
            agent.setNicDocumentType(nicDocument.getContentType());
        }

        if (hasPassbookDoc) {
            if (agent.getBankPassbookDocumentUrl() != null) {
                storagePort.deleteFile(agent.getBankPassbookDocumentUrl());
            }
            agent.setBankPassbookDocumentUrl(storagePort.uploadFile(bankPassbookDocument, "agents/passbook", agent.getAgentCode() + "_Passbook"));
            agent.setBankPassbookDocumentName(bankPassbookDocument.getOriginalFilename());
            agent.setBankPassbookDocumentType(bankPassbookDocument.getContentType());
        }
    }
}
