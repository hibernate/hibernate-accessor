package org.hibernate.accessor.asm.aot;

import static org.hibernate.accessor.asm.aot.GenerationUtil.fqcnToName;

import org.hibernate.accessor.AccessorFactory;
import org.hibernate.accessor.Instantiator;
import org.hibernate.accessor.MultiValueAccessorGenerationException;
import org.hibernate.accessor.MultiValueReader;
import org.hibernate.accessor.MultiValueWriter;
import org.hibernate.accessor.ValueReader;
import org.hibernate.accessor.ValueWriter;

import org.objectweb.asm.Opcodes;


/** Constants used by ASM accessor generation. */
public final class GeneratorConstants {
	private GeneratorConstants() {
	}

	/**
	 * Newly generated classes target Java 17, independently of the application's compiler target.
	 * Transformed hosts retain their original class version; ASM's visitor API version is separate.
	 */
	public static final int GENERATED_CLASS_VERSION = Opcodes.V17;

	static final String READER_INTERFACE_INTERNAL = fqcnToName( ValueReader.class.getName() );
	static final String WRITER_INTERFACE_INTERNAL = fqcnToName( ValueWriter.class.getName() );
	static final String INSTANTIATOR_INTERFACE_INTERNAL = fqcnToName( Instantiator.class.getName() );
	static final String FACTORY_INTERFACE_INTERNAL = fqcnToName( AccessorFactory.class.getName() );
	static final String MULTI_VALUE_READER_INTERFACE_INTERNAL = fqcnToName( MultiValueReader.class.getName() );
	static final String MULTI_VALUE_WRITER_INTERFACE_INTERNAL = fqcnToName( MultiValueWriter.class.getName() );
	static final String MULTI_VALUE_GENERATION_EXCEPTION_INTERNAL = fqcnToName( MultiValueAccessorGenerationException.class.getName() );
	static final String METHOD_NAME_HOST_CLASS = "$$__hibernateAccessor_hostClass";
	static final String METHOD_NAME_FIELD_READER_ACCESSOR = "$$__hibernateAccessor_fieldReader";
	static final String METHOD_NAME_METHOD_READER_ACCESSOR = "$$__hibernateAccessor_methodReader";
	static final String METHOD_NAME_FIELD_WRITER_ACCESSOR = "$$__hibernateAccessor_fieldWriter";
	static final String METHOD_NAME_METHOD_WRITER_ACCESSOR = "$$__hibernateAccessor_methodWriter";
	static final String METHOD_NAME_INSTANTIATOR_ACCESSOR = "$$__hibernateAccessor_instantiator";

	static final String PREFIX_READ_METHOD = "$$__hibernateAccessor_read";
	static final String PREFIX_WRITE_METHOD = "$$__hibernateAccessor_write";
	static final String PREFIX_CREATE_METHOD = "$$__hibernateAccessor_create";
}
