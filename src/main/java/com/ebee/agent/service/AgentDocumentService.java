package com.ebee.agent.service;

import com.ebee.agent.entity.Agent;
import com.ebee.common.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AgentDocumentService {

    private final S3Service s3Service;

    public void uploadNewDocuments(Agent agent, MultipartFile nicDocument, MultipartFile bankPassbookDocument) {
        List<CompletableFuture<String>> uploadFutures = new ArrayList<>();

        boolean hasNicDoc = nicDocument != null && !nicDocument.isEmpty();
        boolean hasPassbookDoc = bankPassbookDocument != null && !bankPassbookDocument.isEmpty();

        uploadFutures.add(hasNicDoc
                ? s3Service.uploadFile(nicDocument, "agents/nic")
                : CompletableFuture.completedFuture(null));
        uploadFutures.add(hasPassbookDoc
                ? s3Service.uploadFile(bankPassbookDocument, "agents/passbook")
                : CompletableFuture.completedFuture(null));

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();

        if (hasNicDoc) {
            agent.setNicDocumentUrl(uploadFutures.get(0).join());
            agent.setNicDocumentName(nicDocument.getOriginalFilename());
            agent.setNicDocumentType(nicDocument.getContentType());
        }

        if (hasPassbookDoc) {
            agent.setBankPassbookDocumentUrl(uploadFutures.get(1).join());
            agent.setBankPassbookDocumentName(bankPassbookDocument.getOriginalFilename());
            agent.setBankPassbookDocumentType(bankPassbookDocument.getContentType());
        }
    }

    public void replaceDocuments(Agent agent, MultipartFile nicDocument, MultipartFile bankPassbookDocument) {
        List<CompletableFuture<String>> uploadFutures = new ArrayList<>();

        boolean hasNicDoc = nicDocument != null && !nicDocument.isEmpty();
        boolean hasPassbookDoc = bankPassbookDocument != null && !bankPassbookDocument.isEmpty();

        if (hasNicDoc) {
            if (agent.getNicDocumentUrl() != null) {
                s3Service.deleteFile(agent.getNicDocumentUrl());
            }
            uploadFutures.add(s3Service.uploadFile(nicDocument, "agents/nic"));
        }

        if (hasPassbookDoc) {
            if (agent.getBankPassbookDocumentUrl() != null) {
                s3Service.deleteFile(agent.getBankPassbookDocumentUrl());
            }
            uploadFutures.add(s3Service.uploadFile(bankPassbookDocument, "agents/passbook"));
        }

        if (!uploadFutures.isEmpty()) {
            CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        }

        int futureIndex = 0;
        if (hasNicDoc) {
            agent.setNicDocumentUrl(uploadFutures.get(futureIndex++).join());
            agent.setNicDocumentName(nicDocument.getOriginalFilename());
            agent.setNicDocumentType(nicDocument.getContentType());
        }

        if (hasPassbookDoc) {
            agent.setBankPassbookDocumentUrl(uploadFutures.get(futureIndex).join());
            agent.setBankPassbookDocumentName(bankPassbookDocument.getOriginalFilename());
            agent.setBankPassbookDocumentType(bankPassbookDocument.getContentType());
        }
    }
}
