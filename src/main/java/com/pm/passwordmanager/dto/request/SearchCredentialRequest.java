package com.pm.passwordmanager.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索凭证请求")
public class SearchCredentialRequest {

    @Schema(description = "搜索关键词，匹配账户名称、用户名或 URL", example = "github")
    private String keyword;
}
