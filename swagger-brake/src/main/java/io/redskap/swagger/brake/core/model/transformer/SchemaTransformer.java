package io.redskap.swagger.brake.core.model.transformer;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.*;

import io.redskap.swagger.brake.core.model.Schema;
import io.redskap.swagger.brake.core.model.SchemaAttribute;
import io.redskap.swagger.brake.core.model.schemastore.SchemaStore;
import io.redskap.swagger.brake.core.model.schemastore.SchemaStoreProvider;
import io.redskap.swagger.brake.core.model.service.TypeRefNameResolver;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaTransformer implements Transformer<io.swagger.v3.oas.models.media.Schema, Schema> {
    private final TypeRefNameResolver typeRefNameResolver;

    @Override
    public Schema transform(io.swagger.v3.oas.models.media.Schema from) {
        try {
            return internalTransform(from);
        } finally {
            SeenRefHolder.clear();
        }
    }

    private Schema internalTransform(io.swagger.v3.oas.models.media.Schema swSchema) {
        if (swSchema == null) {
            return null;
        }
        if (swSchema instanceof ArraySchema) {
            Schema schema = internalTransform(((ArraySchema) swSchema).getItems());
            return new Schema.Builder(swSchema.getType()).schema(schema).schemaAttributes(getSchemaAttributes(swSchema)).build();
        } else if (swSchema instanceof ComposedSchema) {
            final Collection<SchemaAttribute> objectAttributes = ((ComposedSchema) swSchema).getAllOf()
                .stream()
                .map(this::transformSchema)
                .map(Schema::getSchemaAttributes)
                .flatMap(Collection::stream)
                .collect(toSet());
            return new Schema.Builder(SchemaTypeUtil.OBJECT_TYPE).schemaAttributes(objectAttributes).build();
        } else {
            return transformSchema(swSchema);
        }
    }

    private Schema transformSchema(io.swagger.v3.oas.models.media.Schema swSchema) {
        String ref = swSchema.get$ref();
        if (isNotBlank(ref) && SeenRefHolder.isNotSeen(ref)) {
            io.swagger.v3.oas.models.media.Schema resolvedSchema = getSchemaForRef(ref);
            SeenRefHolder.store(ref);
            Schema schema = internalTransform(resolvedSchema);
            SeenRefHolder.remove(ref);
            return schema;
        }

        Schema.Builder schemaBuilder = new Schema.Builder(swSchema.getType());
        schemaBuilder.schemaAttributes(getSchemaAttributes(swSchema));
        List<String> enumValues = swSchema.getEnum();
        if (CollectionUtils.isNotEmpty(enumValues)) {
            schemaBuilder.enumValues(enumValues);
        }
        return schemaBuilder.build();
    }

    private List<SchemaAttribute> getSchemaAttributes(io.swagger.v3.oas.models.media.Schema swSchema) {
        Map<String, io.swagger.v3.oas.models.media.Schema> properties = swSchema.getProperties();
        if (properties == null) {
            return Collections.emptyList();
        }
        return properties.entrySet()
                    .stream()
                    .map(e -> {
                        io.swagger.v3.oas.models.media.Schema newInternalSchema = e.getValue();
                        Schema schema = internalTransform(newInternalSchema);
                        return new SchemaAttribute(e.getKey(), schema);
                    })
                    .collect(toList());
    }

    private io.swagger.v3.oas.models.media.Schema getSchemaForRef(String originalRefName) {
        if (originalRefName == null) {
            return null;
        }
        String refName = typeRefNameResolver.resolve(originalRefName);
        SchemaStore schemaStore = SchemaStoreProvider.provide();
        if (schemaStore == null) {
            return null;
        }
        return schemaStore.get(refName).orElseThrow(() -> new IllegalStateException("Reference not found for " + refName));
    }

    /*
     * The purpose of this class is to keep track of already seen schema references to avoid recursive schemas breaking the functionality.
     */
    private static class SeenRefHolder {
        private static final ThreadLocal<Collection<String>> HOLDER = new ThreadLocal<>();

        private static Collection<String> get() {
            Collection<String> seenRefs = HOLDER.get();
            if (seenRefs == null) {
                seenRefs = new HashSet<>();
                HOLDER.set(seenRefs);
            }
            return seenRefs;
        }

        static boolean isSeen(String refName) {
            return get().contains(refName);
        }

        static boolean isNotSeen(String refName) {
            return !isSeen(refName);
        }

        static void store(String refName) {
            get().add(refName);
        }

        static void remove(String refName) {
            get().remove(refName);
        }

        static void clear() {
            HOLDER.remove();
        }
    }
}
