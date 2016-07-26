
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ladislav Hlom
 * Metody pro práci s LDA 
 *
 */
public class LDAmethods {
		
	
	/** Získání reprezentace dokumentu v LDA pro zvolený dokument
	 * @param model - LDA model
	 * @param words - seznam slov dokumentu k převodu 
	 * @return - vrací dokument v LDA reprezentaci
	 */
	public List<Double> getVector(ParallelLDA model, String[] words) {
		model.setActualDocument(words);
		
		List<Double> vector = new ArrayList<Double>();
		
		for (int i=0; i< model.getNumberOfTopics(); i++) {
			vector.add((double) model.getTopicProbability(i));
		}	
		return vector;
	}
	
	
	/** Příprava dokumentu na jeho převod do LDA formy
	 * @param doc - seznam slov dokumentu k převodu 
	 * @param agenture - agentura dokumentu, z které se získá jazyk 
	 * @return vrací převedený dokument
	 */
	public String[] prepareDocument(List<String> doc, String agenture) {
		String preposition = "CZ_";
		
		if (agenture.equalsIgnoreCase("ap")) {
			preposition = "EN_";
		}
		
		String[] stockArr = new String[doc.size()];
		stockArr = doc.toArray(stockArr);
		
		for (int i=0; i<stockArr.length; i++) stockArr[i] = preposition + stockArr[i];
		
		return stockArr;
	}
	
}


