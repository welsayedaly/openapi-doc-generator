package com.github.chhorz.openapi.spring;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;

import com.github.chhorz.javadoc.JavaDoc;
import com.github.chhorz.javadoc.JavaDocParser;
import com.github.chhorz.javadoc.JavaDocParserBuilder;
import com.github.chhorz.javadoc.OutputType;
import com.github.chhorz.javadoc.tags.CategoryTag;
import com.github.chhorz.javadoc.tags.ParamTag;
import com.github.chhorz.openapi.common.OpenApiProcessor;
import com.github.chhorz.openapi.common.domain.Components;
import com.github.chhorz.openapi.common.domain.MediaType;
import com.github.chhorz.openapi.common.domain.OpenAPI;
import com.github.chhorz.openapi.common.domain.Operation;
import com.github.chhorz.openapi.common.domain.Parameter;
import com.github.chhorz.openapi.common.domain.Parameter.In;
import com.github.chhorz.openapi.common.domain.PathItemObject;
import com.github.chhorz.openapi.common.domain.Responses;
import com.github.chhorz.openapi.common.domain.Schema;
import com.github.chhorz.openapi.common.file.FileWriter;
import com.github.chhorz.openapi.common.properties.SpecGeneratorPropertyLoader;
import com.github.chhorz.openapi.common.util.ReferenceUtils;
import com.github.chhorz.openapi.common.util.ResponseUtils;
import com.github.chhorz.openapi.common.util.SchemaUtils;
import com.github.chhorz.openapi.spring.util.AliasUtils;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SpringWebOpenApiProcessor extends AbstractProcessor implements OpenApiProcessor {

	private Elements elements;
	private Types types;

	private SpecGeneratorPropertyLoader propertyLoader;
	private OpenAPI openApi;
	private Components components;

	@Override
	public synchronized void init(final ProcessingEnvironment processingEnv) {
		elements = processingEnv.getElementUtils();
		types = processingEnv.getTypeUtils();

		// initialize property loader
		propertyLoader = new SpecGeneratorPropertyLoader(processingEnv.getOptions());

		// create OpenAPI object
		openApi = new OpenAPI();
		openApi.setOpenapi("3.0.1");
		openApi.setInfo(propertyLoader.createInfoFromProperties());
		openApi.addServer(propertyLoader.createServerFromProperties());
		openApi.setExternalDocs(propertyLoader.createExternalDocsFromProperties());

		components = new Components();
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Stream.of(RequestMapping.class.getCanonicalName()).collect(Collectors.toSet());
	}

	@Override
	public Set<String> getSupportedOptions() {
		return Stream.of("propertiesPath").collect(Collectors.toSet());
	}

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

		for (TypeElement annotation : annotations) {
			roundEnv.getElementsAnnotatedWith(annotation).forEach(element -> {

				if (element instanceof ExecutableElement) {
					ExecutableElement executableElement = (ExecutableElement) element;
					mapOperationMethod(executableElement);
				}

			});
		}

		openApi.setComponents(components);

		File file = new File("./target/openapi.json");
		try {
			file.createNewFile();

			FileWriter fileWriter = new FileWriter(file);
			fileWriter.writeToFile(openApi);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private void mapOperationMethod(final ExecutableElement executableElement) {

		JavaDocParser parser = JavaDocParserBuilder.withBasicTags().withOutputType(OutputType.PLAIN).build();

		JavaDoc javaDoc = parser.parse(elements.getDocComment(executableElement));

		if (executableElement.getAnnotation(RequestMapping.class) != null) {
			RequestMapping requestMapping = executableElement.getAnnotation(RequestMapping.class);

			String[] urlPaths;
			if (requestMapping.path() != null) {
				urlPaths = requestMapping.path();
			} else {
				urlPaths = requestMapping.value();
			}

			for (String path : urlPaths) {

				PathItemObject pathItemObject = new PathItemObject();

				RequestMethod[] requestMethods = requestMapping.method();

				for (RequestMethod requestMethod : requestMethods) {

					Operation operation = new Operation();
					operation.setSummary("");
					operation.setDescription(javaDoc.getDescription());
					operation.setOperationId(String.format("%s#%s", executableElement.getEnclosingElement().getSimpleName(),
							executableElement.getSimpleName()));
					operation.setDeprecated(executableElement.getAnnotation(Deprecated.class) != null);

					List<ParamTag> tags = javaDoc.getTags(ParamTag.class);

					operation.addParameterObjects(executableElement.getParameters()
							.stream()
							.filter(variableElement -> variableElement.getAnnotation(PathVariable.class) != null)
							.map(v -> mapPathVariable(path, v, tags))
							.collect(Collectors.toList()));

					operation.addParameterObjects(executableElement.getParameters()
							.stream()
							.filter(variableElement -> variableElement.getAnnotation(RequestParam.class) != null)
							.map(v -> mapRequestParam(v, tags))
							.collect(Collectors.toList()));

					operation.addParameterObjects(executableElement.getParameters()
							.stream()
							.filter(variableElement -> variableElement.getAnnotation(RequestHeader.class) != null)
							.map(v -> mapRequestHeader(v, tags))
							.collect(Collectors.toList()));

					VariableElement requestBody = executableElement.getParameters()
							.stream()
							.filter(variableElement -> variableElement.getAnnotation(RequestBody.class) != null)
							.findFirst()
							.orElse(null);

					SchemaUtils schemaUtils = new SchemaUtils();
					if (requestBody != null) {
						com.github.chhorz.openapi.common.domain.RequestBody r = new com.github.chhorz.openapi.common.domain.RequestBody();

						r.setDescription("");
						r.setRequired(Boolean.TRUE);

						for (String produces : requestMapping.consumes()) {
							MediaType mediaType = new MediaType();
							mediaType.setSchemaReference(ReferenceUtils.createSchemaReference(requestBody.asType()));

							r.putContent(produces, mediaType);
						}

						components.putAllSchemas(schemaUtils.mapTypeMirrorToSchema(elements, types, requestBody.asType()));

						components.putRequestBody(requestBody.asType().toString(), r);

						operation.setRequestBodyReference(ReferenceUtils.createRequestBodyReference(requestBody.asType()));
					}

					ResponseUtils responseUtils = new ResponseUtils();

					Responses responses = new Responses();
					TypeMirror returnType = executableElement.getReturnType();

					Map<String, Schema> schemaMap = schemaUtils.mapTypeMirrorToSchema(elements, types, returnType);
					Schema schema = schemaMap.get(returnType.toString().substring(returnType.toString().lastIndexOf('.') + 1));
					System.out.println("Return-type: " + schema);
					if ("object".equals(schema.getType()) || "enum".equals(schema.getType())) {
						responses.setDefaultResponse(
								responseUtils.mapTypeMirrorToResponse(types, returnType, requestMapping.produces()));
					} else {
						responses.setDefaultResponse(
								responseUtils.mapSchemaToResponse(types, schema, requestMapping.produces()));
						schemaMap.remove(returnType.toString().substring(returnType.toString().lastIndexOf('.') + 1));
					}

					components.putAllSchemas(schemaMap);

					operation.setResponses(responses);

					javaDoc.getTags(CategoryTag.class)
							.stream().map(CategoryTag::getCategoryName)
							.forEach(tag -> operation.addTag(tag));

					switch (requestMethod) {
						case GET:
							pathItemObject.setGet(operation);
							break;
						case DELETE:
							pathItemObject.setDelete(operation);
							break;
						case HEAD:
							pathItemObject.setHead(operation);
							break;
						case OPTIONS:
							pathItemObject.setOptions(operation);
							break;
						case PATCH:
							pathItemObject.setPatch(operation);
							break;
						case POST:
							pathItemObject.setPost(operation);
							break;
						case PUT:
							pathItemObject.setPut(operation);
							break;
						case TRACE:
							pathItemObject.setTrace(operation);
							break;

						default:
							throw new RuntimeException("Unknown RequestMethod value.");
					}

				}

				openApi.putPathItemObject(path, pathItemObject);
			}
		}
	}

	private Parameter mapPathVariable(final String path, final VariableElement variableElement,
			final List<ParamTag> parameterDocs) {
		PathVariable pathVariable = variableElement.getAnnotation(PathVariable.class);

		AliasUtils<PathVariable> aliasUtils = new AliasUtils<>();
		final String name = aliasUtils.getValue(pathVariable, PathVariable::name, PathVariable::value,
				variableElement.getSimpleName().toString());

		Optional<ParamTag> parameterDescription = parameterDocs.stream()
				.filter(tag -> tag.getParamName().equalsIgnoreCase(name))
				.findFirst();

		Parameter parameter = new Parameter();
		parameter.setAllowEmptyValue(Boolean.FALSE);
		parameter.setDeprecated(variableElement.getAnnotation(Deprecated.class) != null);
		parameter.setDescription(parameterDescription.isPresent() ? parameterDescription.get().getParamDescription() : "");
		parameter.setIn(In.path);
		parameter.setName(name);
		parameter.setRequired(Boolean.TRUE);

		SchemaUtils schemaUtils = new SchemaUtils();
		Map<String, Schema> map = schemaUtils.mapTypeMirrorToSchema(elements, types, variableElement.asType());
		Schema schema = map
				.get(variableElement.asType().toString().substring(variableElement.asType().toString().lastIndexOf('.') + 1));

		Optional<String> regularExpression = getRegularExpression(path, name);
		if (regularExpression.isPresent()) {
			schema.setPattern(regularExpression.get());
		}

		parameter.setSchema(schema);

		return parameter;
	}

	private Parameter mapRequestParam(final VariableElement variableElement, final List<ParamTag> parameterDocs) {
		RequestParam requestParam = variableElement.getAnnotation(RequestParam.class);

		AliasUtils<RequestParam> aliasUtils = new AliasUtils<>();
		final String name = aliasUtils.getValue(requestParam, RequestParam::name, RequestParam::value,
				variableElement.getSimpleName().toString());

		Optional<ParamTag> parameterDescription = parameterDocs.stream()
				.filter(tag -> tag.getParamName().equalsIgnoreCase(name))
				.findFirst();

		Parameter parameter = new Parameter();
		parameter.setAllowEmptyValue(!ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue()));
		parameter.setDeprecated(variableElement.getAnnotation(Deprecated.class) != null);
		parameter.setDescription(parameterDescription.isPresent() ? parameterDescription.get().getParamDescription() : "");
		parameter.setIn(In.query);
		parameter.setName(name);
		parameter.setRequired(requestParam.required());

		SchemaUtils schemaUtils = new SchemaUtils();
		Schema schema = schemaUtils.mapTypeMirrorToSchema(elements, types, variableElement.asType())
				.get(variableElement.asType().toString().substring(variableElement.asType().toString().lastIndexOf('.') + 1));
		if (!ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue())) {
			schema.setDefaultValue(requestParam.defaultValue());
		}
		parameter.setSchema(schema);

		return parameter;
	}

	private Parameter mapRequestHeader(final VariableElement variableElement, final List<ParamTag> parameterDocs) {
		RequestHeader requestHeader = variableElement.getAnnotation(RequestHeader.class);

		AliasUtils<RequestHeader> aliasUtils = new AliasUtils<>();
		final String name = aliasUtils.getValue(requestHeader, RequestHeader::name, RequestHeader::value,
				variableElement.getSimpleName().toString());

		Optional<ParamTag> parameterDescription = parameterDocs.stream()
				.filter(tag -> tag.getParamName().equalsIgnoreCase(name))
				.findFirst();

		// TODO handle MultiValueMap

		Parameter parameter = new Parameter();
		parameter.setAllowEmptyValue(!ValueConstants.DEFAULT_NONE.equals(requestHeader.defaultValue()));
		parameter.setDeprecated(variableElement.getAnnotation(Deprecated.class) != null);
		parameter.setDescription(parameterDescription.isPresent() ? parameterDescription.get().getParamDescription() : "");
		parameter.setIn(In.header);
		parameter.setName(name);
		parameter.setRequired(requestHeader.required());

		SchemaUtils schemaUtils = new SchemaUtils();
		Schema schema = schemaUtils.mapTypeMirrorToSchema(elements, types, variableElement.asType())
				.get(variableElement.asType().toString().substring(variableElement.asType().toString().lastIndexOf('.') + 1));
		if (!ValueConstants.DEFAULT_NONE.equals(requestHeader.defaultValue())) {
			schema.setDefaultValue(requestHeader.defaultValue());
		}
		parameter.setSchema(schema);

		return parameter;
	}

	private Optional<String> getRegularExpression(final String path, final String pathVariable) {
		Pattern pathVariablePattern = Pattern.compile(".*\\{" + pathVariable + ":([^\\{\\}]+)\\}.*");
		System.out.println(pathVariablePattern);
		Matcher pathVariableMatcher = pathVariablePattern.matcher(path);
		if (pathVariableMatcher.matches()) {
			return Optional.ofNullable(pathVariableMatcher.group(1));
		} else {
			return Optional.empty();
		}
	}
}
