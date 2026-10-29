package org.hibernate.accessor.performance.aot;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact Code attribute lengths and class hashes, emitted outside measurement. */
final class BytecodeReport {
	static void write(String backend, String model, String pattern, int types, int fields, int constructors,
			String chunks, Map<String, byte[]> classes)
			throws Exception {
		String output = System.getProperty( "hibernate.accessor.benchmark.output" );
		if ( output == null )
			return;
		Path directory = Files.createTempDirectory( Files.createDirectories( Path.of( output ) ), backend + "-" );
		Files.writeString( directory.resolve( "configuration.txt" ), "model=" + model + "\npattern=" + pattern
				+ "\nentities=" + types + "\nfields=" + fields + "\nconstructors=" + constructors + "\n" + chunks + "\n" );
		StringBuilder csv = new StringBuilder( "class,method,descriptor,codeBytes,classBytes,sha256\n" );
		for ( var entry : classes.entrySet() ) {
			byte[] bytes = entry.getValue();
			String hash = HexFormat.of().formatHex( MessageDigest.getInstance( "SHA-256" ).digest( bytes ) );
			try ( var in = new DataInputStream( new ByteArrayInputStream( bytes ) ) ) {
				in.skipNBytes( 8 );
				String[] utf = new String[in.readUnsignedShort()];
				for ( int i = 1; i < utf.length; i++ ) {
					switch ( in.readUnsignedByte() ) {
						case 1 -> utf[i] = in.readUTF();
						case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes( 4 );
						case 5, 6 -> {
							in.skipNBytes( 8 );
							i++;
						}
						case 7, 8, 16, 19, 20 -> in.skipNBytes( 2 );
						case 15 -> in.skipNBytes( 3 );
						default -> throw new IllegalArgumentException( "Unknown constant pool tag" );
					}
				}
				in.skipNBytes( 6 );
				in.skipNBytes( 2L * in.readUnsignedShort() );
				int fieldCount = in.readUnsignedShort();
				for ( int f = 0; f < fieldCount; f++ ) {
					in.skipNBytes( 6 );
					int attributes = in.readUnsignedShort();
					for ( int a = 0; a < attributes; a++ ) {
						in.readUnsignedShort();
						in.skipNBytes( Integer.toUnsignedLong( in.readInt() ) );
					}
				}
				int methods = in.readUnsignedShort();
				for ( int m = 0; m < methods; m++ ) {
					in.readUnsignedShort();
					String name = utf[in.readUnsignedShort()];
					String descriptor = utf[in.readUnsignedShort()];
					int attributes = in.readUnsignedShort();
					for ( int a = 0; a < attributes; a++ ) {
						String attribute = utf[in.readUnsignedShort()];
						long length = Integer.toUnsignedLong( in.readInt() );
						if ( "Code".equals( attribute ) ) {
							in.skipNBytes( 4 );
							int codeLength = in.readInt();
							csv.append( entry.getKey() ).append( ',' ).append( name ).append( ',' ).append( descriptor )
									.append( ',' ).append( codeLength ).append( ',' ).append( bytes.length ).append( ',' ).append( hash ).append( '\n' );
							in.skipNBytes( length - 8 );
						}
						else
							in.skipNBytes( length );
					}
				}
			}
		}
		Files.writeString( directory.resolve( "bytecode.csv" ), csv );
	}
}
