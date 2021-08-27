package io.yapix.curl;

import static java.util.Objects.nonNull;

import io.yapix.base.util.ItemUtils;
import io.yapix.model.Api;
import io.yapix.model.Item;
import io.yapix.model.ParameterIn;
import io.yapix.model.RequestBodyType;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * 接口信息生成curl命令
 */
public class CurlGenerator {

    /**
     * 生成curl字符串
     */
    public String generate(Api api) {
        StringBuilder sb = new StringBuilder("curl --location --request ");
        sb.append(api.getMethod().name()).append(" '").append(getUrl(api)).append("' \\\n");
        // 请求头
        for (Item p : api.getParametersByIn(ParameterIn.header)) {
            sb.append("--header '").append(p.getName()).append(": ' \\\n");
        }
        RequestBodyType bodyType = api.getRequestBodyType();
        if (bodyType != null && StringUtils.isNotEmpty(bodyType.getContentType())) {
            sb.append("--header '").append("Content-Type").append(": ").append(bodyType.getContentType())
                    .append("' \\\n");
        }
        // 表单数据
        for (Item p : api.getRequestBodyForm()) {
            sb.append("--data-urlencode '").append(p.getName())
                    .append("=").append(nonNull(p.getDefaultValue()) ? p.getDefaultValue() : "")
                    .append("' \\\n");
        }
        // 请求体
        if (bodyType == RequestBodyType.json && api.getRequestBody() != null) {
            sb.append("--data-raw '").append(ItemUtils.getJsonExample(api.getRequestBody())).append("' \\\n");
        }
        if (sb.charAt(sb.length() - 2) == '\\') {
            sb.deleteCharAt(sb.length() - 2);
        }
        return sb.toString();
    }

    /**
     * 获取地址，包括参数拼接
     */
    private String getUrl(Api api) {
        List<Item> queries = api.getParametersByIn(ParameterIn.query);
        StringBuilder sb = new StringBuilder(api.getPath());
        if (queries.size() > 0) {
            sb.append("?");
            for (Item q : queries) {
                sb.append(q.getName())
                        .append("=").append(nonNull(q.getDefaultValue()) ? q.getDefaultValue() : "")
                        .append("&");
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
