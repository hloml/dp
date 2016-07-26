package documents;


import java.util.ArrayList;

/**
 * @author Ladislav Hlom
 * Třída reprezentující dokument
 *
 */
public class Document {

	private String id;					// identifikator dokumentu
	private String agenture = "";		// typ agentury, podle ktere se urcuje jazyk
	private String message;				// text dokumentu
	private ArrayList<String> categories = new ArrayList<String>();		// natipovane kategorie
	private ArrayList<String> rightCategories = new ArrayList<String>();	// spravne kategorie
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}

	public ArrayList<String> getCategories() {
		return categories;
	}
	public void setCategories(ArrayList<String> categories) {
		this.categories = categories;
	}
	public void addToCategory(String category) {
		categories.add(category);
	}
	
	public void addToRightCategory(String category) {
		rightCategories.add(category);
	}
	
	public void removeFromRightCategory(String category) {
		rightCategories.remove(category);
	}
	
	public ArrayList<String> getRightCategories() {
		return rightCategories;
	}
	
	
	public void setRightCategories(ArrayList<String> rightCategories) {
		this.rightCategories = rightCategories;
	}
	public String getAgenture() {
		return agenture;
	}
	public void setAgenture(String agenture) {
		this.agenture = agenture;
	}
	
}
