package io.yapix.parse.parser;

import static io.yapix.parse.constant.SpringConstants.PathVariable;
import static io.yapix.parse.constant.SpringConstants.RequestAttribute;
import static io.yapix.parse.constant.SpringConstants.RequestBody;
import static io.yapix.parse.constant.SpringConstants.RequestHeader;
import static io.yapix.parse.constant.SpringConstants.RequestParam;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.yapix.config.YapixConfig;
import io.yapix.config.YapixConfig.RequestBodyParamType;
import io.yapix.model.DataTypes;
import io.yapix.model.HttpMethod;
import io.yapix.model.ParameterIn;
import io.yapix.model.Property;
import io.yapix.model.RequestBodyType;
import io.yapix.parse.constant.SpringConstants;
import io.yapix.parse.model.RequestParseInfo;
import io.yapix.parse.util.PsiAnnotationUtils;
import io.yapix.parse.util.PsiDocCommentUtils;
import io.yapix.parse.util.PsiTypeUtils;
import io.yapix.parse.util.WsUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 请求信息解析
 *
 * @see #parse(PsiMethod, HttpMethod)
 */
public class RequestParser {

    private final YapixConfig settings;
    private final KernelParser kernelParser;
    private final ParseHelper parseHelper;
    private final DateParser dateParser;

    public RequestParser(Project project, Module module, YapixConfig settings) {
        this.settings = settings;
        this.kernelParser = new KernelParser(project, module, settings, false);
        this.dateParser = new DateParser(settings);
        this.parseHelper = new ParseHelper(project, module);
    }

    /**
     * 解析请求参数信息
     *
     * @param method     待处理的方法
     * @param httpMethod 当前方法的http请求方法
     */
    public RequestParseInfo parse(PsiMethod method, HttpMethod httpMethod) {
        List<PsiParameter> parameters = filterPsiParameters(method);

        // 解析参数: 请求体类型，普通参数，请求体
        RequestBodyType requestBodyType = getRequestBodyType(method, parameters, httpMethod);
        List<Property> requestParameters = WsUtils.isWsMethod(method) ? new ArrayList<>() : getRequestParameters(method, parameters);
        List<Property> requestBody = getRequestBody(method, parameters, httpMethod, requestParameters);
        // 数据填充
        RequestParseInfo info = new RequestParseInfo();
        info.setParameters(requestParameters);
        info.setRequestBodyType(requestBodyType);
        if (requestBodyType == RequestBodyType.form || requestBodyType == RequestBodyType.form_data) {
            info.setRequestBodyForm(requestBody);
        } else if (!requestBody.isEmpty()) {
            info.setRequestBody(requestBody.get(0));
        }
        if (info.getRequestBodyForm() == null) {
            info.setRequestBodyForm(Collections.emptyList());
        }
        return info;
    }

    /**
     * 解析请求方式
     */
    private RequestBodyType getRequestBodyType(PsiMethod psiMethod, List<PsiParameter> parameters, HttpMethod method) {
        if (!method.isAllowBody()) {
            return null;
        }
        boolean requestBody = WsUtils.isWsMethod(psiMethod) || parameters.stream().anyMatch(p -> p.getAnnotation(RequestBody) != null);
        if (requestBody) {
            return RequestBodyType.json;
        }
        List<ParameterAnnotationPair> requestBodyParamParameters = getRequestBodyParamParameters(parameters);
        if (!requestBodyParamParameters.isEmpty()) {
            return RequestBodyType.json;
        }

        boolean multipartFile = parameters.stream().anyMatch(p -> PsiTypeUtils.isFileIncludeArray(p.getType()));
        if (multipartFile) {
            return RequestBodyType.form_data;
        }
        return RequestBodyType.form;
    }

    /**
     * 获取请求参数注解了自定义@RequestBody的参数
     */
    private List<ParameterAnnotationPair> getRequestBodyParamParameters(List<PsiParameter> parameters) {
        // 自定义@RequestBody类型
        RequestBodyParamType type = settings.getRequestBodyParamType();
        if (type == null) {
            return Collections.emptyList();
        }

        List<ParameterAnnotationPair> pairs = new ArrayList<>();
        for (PsiParameter p : parameters) {
            PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(p, type.getAnnotation());
            if (annotation != null) {
                pairs.add(new ParameterAnnotationPair(p, annotation));
            }
        }
        return pairs;
    }

    /**
     * 解析请求体内容
     */
    private List<Property> getRequestBody(PsiMethod method, List<PsiParameter> parameters, HttpMethod httpMethod,
                                          List<Property> requestParameters) {
        if (!httpMethod.isAllowBody()) {
            return Lists.newArrayList();
        }
        Map<String, String> paramTagMap = PsiDocCommentUtils.getTagParamTextMap(method);
        boolean isWsMethod = WsUtils.isWsMethod(method);
        // Json请求: 找到@RequestBody注解参数, 自定义@RequestBody类型
        Property jsonBodyItem = null;
        PsiParameter bp = parameters.stream()
                .filter(p -> p.getAnnotation(RequestBody) != null).findFirst().orElse(null);
        if (isWsMethod) {
            bp = parameters.get(0);
        }
        if (bp != null) {
            jsonBodyItem = kernelParser.parseType(bp.getType(), bp.getType().getCanonicalText());
            // 方法上的参数描述
            String parameterDescription = paramTagMap.get(bp.getName());
            if (StringUtils.isNotEmpty(parameterDescription)) {
                jsonBodyItem.setDescription(parameterDescription);
            }
        }
        if (isWsMethod) {
            Property wsProperty = WsUtils.getWsProperty(method);
            Property data = wsProperty.getProperties().get("data");
            assert jsonBodyItem != null;
            data.setProperties(jsonBodyItem.getProperties());
            wsProperty.getProperties().put("data", data);
            return Lists.newArrayList(wsProperty);
        }
        List<ParameterAnnotationPair> requestBodyParamParameters = getRequestBodyParamParameters(parameters);
        if (!requestBodyParamParameters.isEmpty()) {
            if (jsonBodyItem == null) {
                jsonBodyItem = new Property();
                jsonBodyItem.setRequired(false);
                jsonBodyItem.setType(DataTypes.OBJECT);
            }
            for (ParameterAnnotationPair pair : requestBodyParamParameters) {
                PsiAnnotation annotation = pair.getAnnotation();
                PsiParameter parameter = pair.getParameter();
                Property property = kernelParser.parseType(parameter.getType(), parameter.getType().getCanonicalText());
                property.setRequired(true);
                String name = PsiAnnotationUtils.getStringAttributeValueByAnnotation(annotation,
                        settings.getRequestBodyParamType().getProperty());
                if (StringUtils.isEmpty(name)) {
                    name = parameter.getName();
                }
                String description = paramTagMap.get(parameter.getName());
                if (StringUtils.isNotEmpty(description)) {
                    property.setDescription(description);
                }
                jsonBodyItem.addProperty(name, property);
            }
        }


        if (jsonBodyItem != null) {
            return Lists.newArrayList(jsonBodyItem);
        }

        // 文件
        List<Property> items = Lists.newArrayList();
        List<PsiParameter> fileParameters = parameters.stream()
                .filter(p -> PsiTypeUtils.isFileIncludeArray(p.getType())).collect(Collectors.toList());
        for (PsiParameter p : fileParameters) {
            Property item = kernelParser.parseType(p.getType(), p.getType().getCanonicalText());
            item.setType(DataTypes.FILE);
            item.setName(p.getName());
            item.setRequired(true);
            // 方法上的参数描述
            String parameterDescription = paramTagMap.get(p.getName());
            if (StringUtils.isNotEmpty(parameterDescription)) {
                item.setDescription(parameterDescription);
            }
            items.add(item);
        }

        // 表单：查询参数合并查询参数到表单
        if (requestParameters != null) {
            List<Property> queries = requestParameters.stream()
                    .filter(p -> p.getIn() == ParameterIn.query).collect(Collectors.toList());
            for (Property query : queries) {
                query.setIn(null);
                items.add(query);
            }
            requestParameters.removeAll(queries);
        }
        return items;
    }

    /**
     * 解析普通参数
     */
    public List<Property> getRequestParameters(PsiMethod method, List<PsiParameter> parameterList) {
        List<PsiParameter> parameters = parameterList.stream()
                .filter(p -> p.getAnnotation(RequestBody) == null)
                .filter(p -> {
                    // 过滤掉自定义@RequestBody类型的参数
                    RequestBodyParamType requestBodyParamType = settings.getRequestBodyParamType();
                    if (requestBodyParamType == null) {
                        return true;
                    }
                    PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(p,
                            requestBodyParamType.getAnnotation());
                    return annotation == null;
                })
                .filter(p -> !PsiTypeUtils.isFileIncludeArray(p.getType()))
                .collect(Collectors.toList());
        // 获取方法@param标记信息
        Map<String, String> paramTagMap = PsiDocCommentUtils.getTagParamTextMap(method);

        List<Property> items = Lists.newArrayListWithExpectedSize(parameters.size());
        for (PsiParameter parameter : parameters) {
            Property item = doParseParameter(parameter);
            item.setDescription(parseHelper.getParameterDescription(parameter, paramTagMap, item.getPropertyValues()));
            // 当参数是bean时，需要获取包括参数
            List<Property> parameterItems = resolveItemToParameters(item);
            items.addAll(parameterItems);
        }
        items.forEach(this::doSetPropertyDateFormat);
        return items;
    }

    private void doSetPropertyDateFormat(Property item) {
        // 附加时间格式
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(item.getDescription())) {
            sb.append(item.getDescription());
        }
        if (StringUtils.isNotEmpty(item.getDateFormat())) {
            if (sb.length() > 0) {
                sb.append(" : ");
            }
            sb.append(item.getDateFormat());
        }
        item.setDescription(sb.toString());
    }

    /**
     * 解析单个参数
     */
    private Property doParseParameter(PsiParameter parameter) {
        Property item = kernelParser.parseType(parameter.getType(), parameter.getType().getCanonicalText());
        dateParser.handle(item, parameter);

        // 处理参数注解: @RequestParam等
        PsiAnnotation annotation = null;
        ParameterIn in = ParameterIn.query;
        Map<String, ParameterIn> targets = new LinkedHashMap<>();
        targets.put(RequestParam, ParameterIn.query);
        targets.put(RequestAttribute, ParameterIn.query);
        targets.put(RequestHeader, ParameterIn.header);
        targets.put(PathVariable, ParameterIn.path);
        for (Entry<String, ParameterIn> target : targets.entrySet()) {
            annotation = PsiAnnotationUtils.getAnnotation(parameter, target.getKey());
            if (annotation != null) {
                in = target.getValue();
                break;
            }
        }
        // 字段名称
        Boolean required = null;
        String name = null;
        String defaultValue = null;
        if (annotation != null) {
            name = PsiAnnotationUtils.getStringAttributeValueByAnnotation(annotation, "name");
            if (StringUtils.isEmpty(name)) {
                name = PsiAnnotationUtils.getStringAttributeValueByAnnotation(annotation, "value");
            }
            required = AnnotationUtil.getBooleanAttributeValue(annotation, "required");
            defaultValue = PsiAnnotationUtils.getStringAttributeValueByAnnotation(annotation, "defaultValue");
            if (SpringConstants.DEFAULT_NONE.equals(defaultValue)) {
                defaultValue = null;
            }
        }
        if (StringUtils.isEmpty(name)) {
            name = parameter.getName();
        }
        if (required == null) {
            required = parseHelper.getParameterRequired(parameter);
        }

        item.setIn(in);
        item.setName(name);
        item.setRequired(required != null ? required : false);
        item.setDefaultValue(defaultValue);
        return item;
    }

    /**
     * 解析Item为扁平结构的parameter
     */
    private List<Property> resolveItemToParameters(Property item) {
        if (item == null) {
            return Collections.emptyList();
        }
        boolean needFlat = item.isObjectType() && item.getProperties() != null && ParameterIn.query == item.getIn();
        if (needFlat) {
            Collection<Property> flatItems = item.getProperties().values();
            flatItems.forEach(one -> one.setIn(item.getIn()));
            return Lists.newArrayList(flatItems);
        }
        return Lists.newArrayList(item);
    }

    /**
     * 过滤无需处理的参数
     */
    private List<PsiParameter> filterPsiParameters(PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        Set<String> ignoreTypes = Sets.newHashSet(settings.getParameterIgnoreTypes());
        return Arrays.stream(parameters)
                .filter(p -> {
                    String type = p.getType().getCanonicalText();
                    return !ignoreTypes.contains(type);
                }).collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    private static class ParameterAnnotationPair {
        private PsiParameter parameter;
        private PsiAnnotation annotation;
    }
}
