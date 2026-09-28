package entities;

public class Entity {
	private String value;
	private final String finalValue;

	private Entity() {
		this( "initial" );
	}

	private Entity(String value) {
		this.value = value;
		this.finalValue = value;
	}

	private String getValue() {
		return value;
	}

	private void setValue(String value) {
		this.value = value;
	}
}
