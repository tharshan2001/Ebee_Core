package com.ebee.common.port;

import org.springframework.web.multipart.MultipartFile;

public interface StoragePort {

    String uploadFile(MultipartFile file, String folder);

    void deleteFile(String fileUrl);
}
