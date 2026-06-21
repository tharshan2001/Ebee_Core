package com.ebee.payout.mapper;

import com.ebee.payout.dto.PayoutResponse;
import com.ebee.payout.entity.Payout;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PayoutMapper {

    PayoutResponse toResponse(Payout payout);

    List<PayoutResponse> toResponseList(List<Payout> payouts);
}
