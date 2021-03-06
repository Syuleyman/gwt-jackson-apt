/*
 * Copyright 2017 Ahmad Bawaneh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dominokit.jacksonapt.processor.deserialization;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.dominokit.jacksonapt.deser.EnumJsonDeserializer;
import org.dominokit.jacksonapt.deser.array.ArrayJsonDeserializer;
import org.dominokit.jacksonapt.deser.array.dd.Array2dJsonDeserializer;
import org.dominokit.jacksonapt.processor.MappersChainBuilder;
import org.dominokit.jacksonapt.processor.ObjectMapperProcessor;
import org.dominokit.jacksonapt.processor.Type;
import org.dominokit.jacksonapt.processor.TypeRegistry;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.util.Deque;
import java.util.LinkedList;

/**
 * <p>FieldDeserializersChainBuilder class.</p>
 *
 * @author vegegoku
 * @version $Id: $Id
 */
public class FieldDeserializersChainBuilder implements MappersChainBuilder {
    private static final String GET_INSTANCE = "$T.getInstance()";
    private static final String NEW_INSTANCE = "$T.newInstance(";

    private CodeBlock.Builder builder = CodeBlock.builder();
    private Deque<TypeName> deserializers = new LinkedList<>();
    private TypeMirror beanType;

    /**
     * <p>Constructor for FieldDeserializersChainBuilder.</p>
     *
     * @param beanType a {@link javax.lang.model.type.TypeMirror} object.
     */
    public FieldDeserializersChainBuilder(TypeMirror beanType) {
        this.beanType = beanType;
    }

    /** {@inheritDoc} */
    @Override
    public CodeBlock getInstance(Element field) {
        return builder.add(getFieldDeserializer(field.asType()), asClassesArray()).build();
    }

    private TypeName[] asClassesArray() {
        return deserializers.toArray(new TypeName[deserializers.size()]);
    }

    private String getFieldDeserializer(TypeMirror typeMirror) {
        if (Type.isIterable(typeMirror))
            return getIterableDeserializer(typeMirror);
        if (Type.isMap(typeMirror))
            return getMapDeserializer(typeMirror);
        if (Type.isArray(typeMirror))
            return getArrayDeserializer(typeMirror);
        if (Type.isEnum(typeMirror))
            return getEnumDeserializer(typeMirror);
        return getBasicOrCustomDeserializer(typeMirror);
    }

    private String getBasicOrCustomDeserializer(TypeMirror typeMirror) {
        if (Type.isBasicType(typeMirror))
            return getBasicDeserializer(typeMirror);
        return getCustomDeserializer(typeMirror);
    }

    private String getCustomDeserializer(TypeMirror typeMirror) {
        if (typeMirror.toString().equals(beanType.toString())) {
            deserializers.addLast(ClassName.bestGuess(Type.deserializerName(typeMirror)));
        } else {
            if (TypeRegistry.containsDeserializer(typeMirror)) {
                deserializers.addLast(TypeRegistry.getCustomDeserializer(typeMirror));
            } else {
                TypeRegistry.registerDeserializer(typeMirror.toString(), ClassName.bestGuess(generateCustomDeserializer(typeMirror)));
                deserializers.addLast(TypeRegistry.getCustomDeserializer(typeMirror));
            }
        }
        return "new $T()";
    }

    private String generateCustomDeserializer(TypeMirror typeMirror) {
        return Type.generateDeserializer(typeMirror);
    }

    private String getEnumDeserializer(TypeMirror typeMirror) {
        deserializers.addLast(TypeName.get(EnumJsonDeserializer.class));
        deserializers.addLast(TypeName.get(typeMirror));
        return NEW_INSTANCE + "$T.class)";
    }

    private String getBasicDeserializer(TypeMirror typeMirror) {
        deserializers.addLast(TypeRegistry.getDeserializer(typeMirror));
        return GET_INSTANCE;
    }

    private String getMapDeserializer(TypeMirror typeMirror) {
        deserializers.addLast(TypeName.get(TypeRegistry.getMapDeserializer(typeMirror)));
        return NEW_INSTANCE + getKeyDeserializer(Type.firstTypeArgument(typeMirror)) + ", " + getFieldDeserializer(Type.secondTypeArgument(typeMirror)) + ")";
    }

    private String getKeyDeserializer(TypeMirror typeMirror) {
        if (Type.isEnum(typeMirror))
            return getEnumKeyDeserializer(typeMirror);
        return getBasicKeyDeserializer(typeMirror);
    }

    private String getBasicKeyDeserializer(TypeMirror typeMirror) {
        deserializers.addLast(TypeRegistry.getKeyDeserializer(typeMirror.toString()));
        return GET_INSTANCE;
    }

    private String getEnumKeyDeserializer(TypeMirror typeMirror) {
        deserializers.addLast(TypeRegistry.getKeyDeserializer(Enum.class.getName()));
        deserializers.addLast(TypeName.get(typeMirror));
        return NEW_INSTANCE + "$T.class" + ")";
    }

    private String getIterableDeserializer(TypeMirror typeMirror) {
        deserializers.addLast(TypeName.get(TypeRegistry.getCollectionDeserializer(typeMirror)));
        return NEW_INSTANCE + getFieldDeserializer(Type.firstTypeArgument(typeMirror)) + ")";
    }

    private String getArrayDeserializer(TypeMirror typeMirror) {
        if (Type.isPrimitiveArray(typeMirror)) {
            return getBasicDeserializer(typeMirror);
        } else if (Type.is2dArray(typeMirror)) {
            deserializers.addLast(TypeName.get(Array2dJsonDeserializer.class));
            return NEW_INSTANCE + getFieldDeserializer(Type.deepArrayComponentType(typeMirror)) + ", " + getArray2dCreatorFormat(typeMirror) + ")";
        } else {
            deserializers.addLast(TypeName.get(ArrayJsonDeserializer.class));
            return NEW_INSTANCE + getFieldDeserializer(Type.arrayComponentType(typeMirror)) + ", " + getArrayCreatorFormat(typeMirror) + ")";
        }
    }

    private String getArrayCreatorFormat(TypeMirror typeMirror) {
        deserializers.addLast(TypeName.get(ArrayJsonDeserializer.ArrayCreator.class));
        deserializers.addLast(TypeName.get(Type.arrayComponentType(typeMirror)));
        deserializers.addLast(TypeName.get(ObjectMapperProcessor.typeUtils.erasure(Type.arrayComponentType(typeMirror))));
        return "($T<$T>) $T[]::new";
    }

    private String getArray2dCreatorFormat(TypeMirror typeMirror) {
        deserializers.addLast(TypeName.get(Array2dJsonDeserializer.Array2dCreator.class));
        deserializers.addLast(TypeName.get(Type.deepArrayComponentType(typeMirror)));
        deserializers.addLast(TypeName.get(ObjectMapperProcessor.typeUtils.erasure(Type.deepArrayComponentType(typeMirror))));
        return "($T<$T>) (first, second) -> new $T[first][second]";
    }
}
