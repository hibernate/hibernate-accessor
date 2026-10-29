package org.hibernate.accessor.asm.aot;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.hibernate.accessor.asm.impl.BytecodeSupport;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/** Name and descriptor helpers for integrations collecting AOT accessor metadata. */
public final class GenerationUtil {

	private GenerationUtil() {
	}

	static String fqcnToName(String fqcn) {
		return fqcn.replace( '.', '/' );
	}

	/** Converts a JVM internal class name to its binary name. */
	public static String nameToFqcn(String name) {
		return name.replace( '/', '.' );
	}

	/**
	 * Returns whether the ASM type represents a primitive value, excluding {@code void}.
	 */
	public static boolean isPrimitive(Type type) {
		int sort = type.getSort();
		return sort >= Type.BOOLEAN && sort <= Type.DOUBLE;
	}

	/** Returns parameter type names in declaration order from a JVM method or constructor descriptor. */
	public static String[] parameterTypes(String descriptor) {
		return Arrays.stream( Type.getArgumentTypes( descriptor ) )
				.map( Type::getClassName ).toArray( String[]::new );
	}

	static void pushIntConst(MethodVisitor mv, int value) {
		BytecodeSupport.emitIntConstant( mv, value );
	}

	@FunctionalInterface
	interface CaseBodyEmitter {
		void emit(MethodVisitor mv, int caseIndex);
	}

	/**
	 * Emits the javac-style two-phase string switch pattern:
	 * Phase 1: LOOKUPSWITCH on hashCode -> equals checks -> set temp variable
	 * Phase 2: TABLESWITCH on temp variable -> case bodies
	 *
	 * <pre>
	 * int caseIndex = -1;
	 * switch (key.hashCode()) {
	 *     case 2236: // "Ea" and "FB" have the same hash
	 *         if (key.equals("Ea"))
	 *             caseIndex = 0;
	 *         else if (key.equals("FB"))
	 *             caseIndex = 1;
	 *         break;
	 * }
	 * switch (caseIndex) {
	 *     case 0: // bodyEmitter.emit(mv, 0)
	 *     case 1: // bodyEmitter.emit(mv, 1)
	 *     default: // jump to defaultLabel
	 * }
	 * </pre>
	 *
	 * The case bodies above are placeholders: the emitter supplies their instructions and control flow.
	 * <p>
	 * Callers also use this helper within two-level chunked dispatch to stay below the JVM's 64 KiB method limit,
	 * as with integer switches: an outer dispatcher selects a helper method using the key's hash, and each helper
	 * uses this string switch for its subset of keys. Callers partition the keys and generate those helper methods;
	 * the two phases shown above describe a single string switch, not the chunking itself.
	 * Hash buckets can be arbitrarily skewed; oversized buckets must be split into bounded helpers
	 * that continue searching on a miss, rather than emitting an unbounded string switch.
	 */
	static void emitStringSwitch(MethodVisitor mv, int stringSlot, int tempSlot,
			List<String> cases, Label defaultLabel, CaseBodyEmitter bodyEmitter) {
		if ( cases.isEmpty() ) {
			mv.visitJumpInsn( GOTO, defaultLabel );
			return;
		}

		// LOOKUPSWITCH requires sorted keys. Keep all cases for each hash: distinct member names can
		// collide, so the hash selects a bucket and equals checks select the actual case.
		TreeMap<Integer, List<Integer>> hashToCaseIndices = new TreeMap<>();
		for ( int i = 0; i < cases.size(); i++ ) {
			int hash = cases.get( i ).hashCode();
			hashToCaseIndices.computeIfAbsent( hash, k -> new ArrayList<>() ).add( i );
		}

		int[] hashes = hashToCaseIndices.keySet().stream().mapToInt( Integer::intValue ).toArray();
		Label[] hashLabels = new Label[hashes.length];
		for ( int i = 0; i < hashes.length; i++ ) {
			hashLabels[i] = new Label();
		}

		Label secondSwitch = new Label();

		pushIntConst( mv, -1 );
		mv.visitVarInsn( ISTORE, tempSlot );

		mv.visitVarInsn( ALOAD, stringSlot );
		mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false );
		mv.visitLookupSwitchInsn( secondSwitch, hashes, hashLabels );

		for ( int h = 0; h < hashes.length; h++ ) {
			mv.visitLabel( hashLabels[h] );
			mv.visitFrame( F_SAME, 0, null, 0, null );

			List<Integer> caseIndices = hashToCaseIndices.get( hashes[h] );
			for ( int c = 0; c < caseIndices.size(); c++ ) {
				int ci = caseIndices.get( c );
				boolean last = ( c == caseIndices.size() - 1 );

				mv.visitVarInsn( ALOAD, stringSlot );
				mv.visitLdcInsn( cases.get( ci ) );
				mv.visitMethodInsn( INVOKEVIRTUAL, "java/lang/String", "equals",
						"(Ljava/lang/Object;)Z", false );

				if ( last ) {
					Label afterSet = new Label();
					mv.visitJumpInsn( IFEQ, afterSet );
					pushIntConst( mv, ci );
					mv.visitVarInsn( ISTORE, tempSlot );
					mv.visitLabel( afterSet );
					mv.visitFrame( F_SAME, 0, null, 0, null );
				}
				else {
					Label nextCheck = new Label();
					mv.visitJumpInsn( IFEQ, nextCheck );
					pushIntConst( mv, ci );
					mv.visitVarInsn( ISTORE, tempSlot );
					mv.visitJumpInsn( GOTO, secondSwitch );
					mv.visitLabel( nextCheck );
					mv.visitFrame( F_SAME, 0, null, 0, null );
				}
			}
			mv.visitJumpInsn( GOTO, secondSwitch );
		}

		mv.visitLabel( secondSwitch );
		mv.visitFrame( F_SAME, 0, null, 0, null );

		Label[] bodyLabels = new Label[cases.size()];
		for ( int i = 0; i < cases.size(); i++ ) {
			bodyLabels[i] = new Label();
		}

		mv.visitVarInsn( ILOAD, tempSlot );
		mv.visitTableSwitchInsn( 0, cases.size() - 1, defaultLabel, bodyLabels );

		for ( int i = 0; i < cases.size(); i++ ) {
			mv.visitLabel( bodyLabels[i] );
			mv.visitFrame( F_SAME, 0, null, 0, null );
			bodyEmitter.emit( mv, i );
		}
	}

}
