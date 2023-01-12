package io.apidocx.parse.parser;

import static io.apidocx.parse.constant.SpringConstants.PathVariable;
import static io.apidocx.parse.constant.SpringConstants.RequestAttribute;
import static io.apidocx.parse.constant.SpringConstants.RequestBody;
import static io.apidocx.parse.constant.SpringConstants.RequestHeader;
import static io.apidocx.parse.constant.SpringConstants.RequestParam;

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
import io.apidocx.config.ApidocxConfig;
import io.apidocx.config.ApidocxConfig.RequestBodyParamType;
import io.apidocx.model.DataTypes;
import io.apidocx.model.HttpMethod;
import io.apidocx.model.ParameterIn;
import io.apidocx.model.Property;
import io.apidocx.model.RequestBodyType;
import io.apidocx.parse.constant.SpringConstants;
import io.apidocx.parse.model.Jsr303Info;
import io.apidocx.parse.model.RequestInfo;
import io.apidocx.parse.model.TypeParseContext;
import io.apidocx.parse.util.PsiAnnotationUtils;
import io.apidocx.parse.util.PsiDocCommentUtils;
import io.apidocx.parse.util.PsiTypeUtils;
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
import org.jetbrains.annotations.NotNull;

/**
 * 请求信息解析
 *
 * @see #parse(PsiMethod, HttpMethod)
 */
public class RequestParser {

    private final ApidocxConfig settings;
    private final KernelParser kernelParser;
    private final ParseHelper parseHelper;
    private final DateParser dateParser;

    public RequestParser(Project project, Module module, ApidocxConfig settings) {
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

    public RequestInfo parse(PsiMethod method, HttpMethod httpMethod) {
        List<PsiParameter> parameters = filterMethodParameters(method);
        // 解析参数: 请求体类型，普通参数，请求体
        RequestBodyType requestBodyType = getRequestBodyType(method, parameters, httpMethod);
        List<Property> requestParameters = WsUtils.isWsMethod(method) ? new ArrayList<>() : getRequestParameters(method, parameters);
        List<Property> requestParameters = getRequestParameters(method, parameters);
        RequestBodyType requestBodyType = getRequestBodyType(parameters, httpMethod);
        List<Property> requestBody = getRequestBody(method, parameters, httpMethod, requestParameters);
        // 数据填充
        RequestParseInfo info = new RequestParseInfo();
        info.setParameters(requestParameters);
        info.setRequestBodyType(requestBodyType);
        if (requestBodyType == RequestBodyType.form || requestBodyType == RequestBodyType.form_data) {
            info.setRequestBodyForm(requestBody);

        RequestInfo request = new RequestInfo();
        request.setParameters(requestParameters);
        request.setRequestBodyType(requestBodyType);
        request.setRequestBodyForm(Collections.emptyList());
        if (requestBodyType != null && requestBodyType.isFormOrFormData()) {
            request.setRequestBodyForm(requestBody);
        } else if (!requestBody.isEmpty()) {
            request.setRequestBody(requestBody.get(0));
        }
        return request;
    }

    /**
     * 解析普通参数
     */
    public List<Property> getRequestParameters(PsiMethod method, List<PsiParameter> parameterList) {
        List<PsiParameter> parameters = filterRequestParameters(parameterList);

        return parameters.stream().flatMap(p -> {
            Property property = doParseParameter(method, p);
            List<Property> properties = flatProperty(property);
            properties.forEach(this::doSetPropertyDateFormat);
            return properties.stream();
        }).collect(Collectors.toList());
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

        boolean multipart = parameters.stream().anyMatch(p -> PsiTypeUtils.isFileIncludeArray(p.getType()));
        if (multipart) {
            return RequestBodyType.form_data;
        }
        return RequestBodyType.form;
    }

    /**
     * 解析请求体内容
     */
    private List<Property> getRequestBody(PsiMethod method, List<PsiParameter> methodParameters, HttpMethod httpMethod,
                                          List<Property> requestParameters) {
        if (!httpMethod.isAllowBody()) {
            return Lists.newArrayList();
        }
        Map<String, String> paramTags = PsiDocCommentUtils.getTagParamTextMap(method);
        Property bodyProperty = null;

        // JSON: 解析@RequestBody注解参数、自定义@RequestBody注解参数
        PsiParameter bodyParameter = methodParameters.stream()
        Map<String, String> paramTagMap = PsiDocCommentUtils.getTagParamTextMap(method);
        boolean isWsMethod = WsUtils.isWsMethod(method);
        // Json请求: 找到@RequestBody注解参数, 自定义@RequestBody类型
        Property jsonBodyItem = null;
        PsiParameter bp = parameters.stream()
                .filter(p -> p.getAnnotation(RequestBody) != null).findFirst().orElse(null);
        if (bodyParameter != null) {
            bodyProperty = kernelParser.parse(bodyParameter.getType());
            String description = paramTags.get(bodyParameter.getName());
            if (StringUtils.isNotEmpty(description)) {
                bodyProperty.setDescription(description);
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
        List<ParameterAnnotationPair> bodyParamParameters = getRequestBodyParamParameters(methodParameters);
        if (!bodyParamParameters.isEmpty()) {
            if (bodyProperty == null) {
                bodyProperty = new Property();
                bodyProperty.setRequired(false);
                bodyProperty.setType(DataTypes.OBJECT);
            }
            for (ParameterAnnotationPair pair : bodyParamParameters) {
                PsiParameter parameter = pair.getParameter();
                PsiAnnotation annotation = pair.getAnnotation();

                Property property = kernelParser.parse(parameter.getType());
                property.setRequired(true);
                property.setDescription(paramTags.get(parameter.getName()));
                String name = PsiAnnotationUtils.getStringAttributeValueByAnnotation(annotation,
                        settings.getRequestBodyParamType().getProperty());
                if (StringUtils.isEmpty(name)) {
                    name = parameter.getName();
                }
                bodyProperty.addProperty(name, property);
            }
        }
        if (bodyProperty != null) {
            return Lists.newArrayList(bodyProperty);
        }

        // 2.文件类型
        List<Property> formProperties = Lists.newArrayList();
        List<PsiParameter> fileParameters = methodParameters.stream()
                .filter(p -> PsiTypeUtils.isFileIncludeArray(p.getType())).collect(Collectors.toList());
        for (PsiParameter p : fileParameters) {
            Property item = kernelParser.parse(p.getType());
            item.setType(DataTypes.FILE);
            item.setName(p.getName());
            item.setRequired(true);
            item.setDescription(paramTags.get(p.getName()));
            formProperties.add(item);
        }
        // 表单：查询参数合并查询参数到表单
        if (requestParameters != null) {
            List<Property> queries = requestParameters.stream()
                    .filter(p -> p.getIn() == ParameterIn.query).collect(Collectors.toList());
            for (Property query : queries) {
                query.setIn(null);
                formProperties.add(query);
            }
            requestParameters.removeAll(queries);
        }
        return formProperties;
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

        List<ParameterAnnotationPair> pairs = new ArrayList<>();
        for (PsiParameter p : parameters) {
            PsiAnnotation annotation = PsiAnnotationUtils.getAnnotation(p, type.getAnnotation());
            if (annotation != null) {
                pairs.add(new ParameterAnnotationPair(p, annotation));
            }
        }
        return pairs;
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
    private Property doParseParameter(PsiMethod method, PsiParameter parameter) {
        // 解析类型中的信息
        TypeParseContext context = createTypeParseContext(method, parameter);
        Property property = kernelParser.parse(context, parameter.getType(), parameter.getType().getCanonicalText());

        // 方法参数级别信息处理
        dateParser.handle(property, parameter);
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
        Map<String, String> paramTags = PsiDocCommentUtils.getTagParamTextMap(method);
        String description = parseHelper.getParameterDescription(parameter, paramTags, property.getPropertyValues());
        property.setIn(in);
        property.setName(name);
        property.setDescription(description);
        property.setRequired(required != null ? required : false);
        property.setDefaultValue(defaultValue);

        // JSR303注解
        Jsr303Info jsr303Info = parseHelper.getJsr303Info(parameter);
        if (jsr303Info.getMinLength() != null) {
            property.setMinLength(jsr303Info.getMinLength());
        }
        if (jsr303Info.getMaxLength() != null) {
            property.setMaxLength(jsr303Info.getMaxLength());
        }
        if (jsr303Info.getMinimum() != null) {
            property.setMinimum(jsr303Info.getMinimum());
        }
        if (jsr303Info.getMaximum() != null) {
            property.setMaximum(jsr303Info.getMaximum());
        }
        return property;
    }

    @NotNull
    private static TypeParseContext createTypeParseContext(PsiMethod method, PsiParameter parameter) {
        PsiAnnotation validatedAnnotation = PsiAnnotationUtils.getAnnotation(parameter, SpringConstants.Validated);
        if (validatedAnnotation == null) {
            validatedAnnotation = PsiAnnotationUtils.getAnnotation(method, SpringConstants.Validated);
        }
        List<String> validatedClasses = Lists.newArrayList();
        if (validatedAnnotation != null) {
            validatedClasses = PsiAnnotationUtils.getStringArrayAttribute(validatedAnnotation, "value");
        }
        TypeParseContext context = new TypeParseContext();
        context.setJsr303ValidateGroups(validatedClasses);
        return context;
    }


    /**
     * 过滤无需处理的参数
     */
    private List<PsiParameter> filterMethodParameters(PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        Set<String> ignoreTypes = Sets.newHashSet(settings.getParameterIgnoreTypes());
        return Arrays.stream(parameters)
                .filter(p -> {
                    String type = p.getType().getCanonicalText();
                    return !ignoreTypes.contains(type);
                }).collect(Collectors.toList());
    }

    private List<PsiParameter> filterRequestParameters(List<PsiParameter> parameters) {
        return parameters.stream()
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
    }

    /**
     * 解析Item为扁平结构的parameter
     */
    private List<Property> flatProperty(Property property) {
        if (property == null) {
            return Collections.emptyList();
        }
        boolean flat = property.isObjectType() && property.getProperties() != null && ParameterIn.query == property.getIn();
        if (flat) {
            Collection<Property> properties = property.getProperties().values();
            properties.forEach(one -> one.setIn(property.getIn()));
            return Lists.newArrayList(properties);
        }
        return Lists.newArrayList(property);
    }

    @Data
    @AllArgsConstructor
    private static class ParameterAnnotationPair {
        private PsiParameter parameter;
        private PsiAnnotation annotation;
    }
}
