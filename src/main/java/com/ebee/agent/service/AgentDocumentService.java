package com.ebee.agent.service;

import com.ebee.agent.entity.Agent;
import com.ebee.common.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AgentDocumentService {

    private final S3Service s3Service;

    public void uploadNewDocuments(Agent agent, MultipartFile nicDocument, MultipartFile bankPassbookDocument) {
        if (nicDocument != null && !nicDocument.isEmpty()) {
            agent.setNicDocumentUrl(s3Service.uploadFile(nicDocument, "agents/nic"));
            agent.setNicDocumentName(nicDocument.getOriginalFilename());
            agent.setNicDocumentType(nicDocument.getContentType());
        }

        if (bankPassbookDocument != null && !bankPassbookDocument.isEmpty()) {
            agent.setBankPassbookDocumentUrl(s3Service.uploadFile(bankPassbookDocument, "agents/passbook"));
            agent.setBankPassbookDocumentName(bankPassbookDocument.getOriginalFilename());
            agent.setBankPassbookDocumentType(bankPassbookDocument.getContentType());
        }
    }

    public void replaceDocuments(Agent agent, MultipartFile nicDocument, MultipartFile bankPassbookDocument) {
        boolean hasNicDoc = nicDocument != null && !nicDocument.isEmpty();
        boolean hasPassbookDoc = bankPassbookDocument != null && !bankPassbookDocument.isEmpty();

        if (hasNicDoc) {
            if (agent.getNicDocumentUrl() != null) {
                s3Service.deleteFile(agent.getNicDocumentUrl());
            }
            agent.setNicDocumentUrl(s3Service.uploadFile(nicDocument, "agents/nic"));
            agent.setNicDocumentName(nicDocument.getOriginalFilename());
            agent.setNicDocumentType(nicDocument.getContentType());
        }

        if (hasPassbookDoc) {
            if (agent.getBankPassbookDocumentUrl() != null) {
                s3Service.deleteFile(agent.getBankPassbookDocumentUrl());
            }
            agent.setBankPassbookDocumentUrl(s3Service.uploadFile(bankPassbookDocument, "agents/passbook"));
            agent.setBankPassbookDocumentName(bankPassbookDocument.getOriginalFilename());
            agent.setBankPassbookDocumentType(bankPassbookDocument.getContentType());
        }
    }
}
