package com.parroto.subscription.dto;

import java.util.List;

public record CreateActivationCodesResponse(List<ActivationCodeResponse> codes) {
}
