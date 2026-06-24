package com.ebee.payout.mapper;

import com.ebee.payout.dto.PayoutResponse;
import com.ebee.payout.entity.Payout;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PayoutMapper {

    PayoutResponse toResponse(Payout payout);

    List<PayoutResponse> toResponseList(List<Payout> payouts);

    @AfterMapping
    default void setPdfUrl(@MappingTarget PayoutResponse response) {
        if (response.getId() != null) {
            response.setPdfUrl("/api/payouts/" + response.getId() + "/pdf");
        }
    }
}
