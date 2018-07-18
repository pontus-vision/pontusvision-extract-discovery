package org.talend.dataprep.conversions.inject;

import java.util.function.BiFunction;

import org.talend.dataprep.api.preparation.PreparationDTO;

/**
 * An API to ease {@link org.talend.dataprep.api.share.Owner} injection in {@link PreparationDTO} instances.
 *
 * @see org.talend.dataprep.conversions.BeanConversionService#convert(Object, Class, BiFunction[])
 */
public interface OwnerInjection extends BiFunction<Object, PreparationDTO, PreparationDTO> {
}