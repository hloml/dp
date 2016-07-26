package dp.classification;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import cz.zcu.fav.liks.ml.features.Feature;
import cz.zcu.fav.liks.ml.features.FeatureVectorGenerator;
import cz.zcu.fav.liks.ml.lists.ListIterator;
import cz.zcu.fav.liks.ml.lists.TrainingInstanceList;
import cz.zcu.fav.liks.ml.lists.TrainingListIterator;



/**
 * @author Ladislav Hlom
 * Příznaky pro klasifikaci metodou LDA. Jako příznaky slouží kosinová podobnost nebo euklidovská vzdálenost se všemi trénovacími dokumenty.
 *
 */
public class LDAFeature implements Feature<List<Double>>{

	private static final long serialVersionUID = 4090768842140272873L;
	private int dimension;							// pocet priznaku
	private int numLabels;							// pocet oznacenych dokumentu
	private boolean cosine = true;					// typ metriky
	private List<List<List<Double>>> docs;			// trenovaci dokumenty

	/** Konstruktor
	 * @param dimension - velikost trénovací kolekce
	 * @param prop - konfigurační nastavení
	 */
	public LDAFeature(int dimension, Properties prop){
		this.dimension = dimension;
		if (prop.getProperty("cosine") != null) 
			this.cosine = Boolean.parseBoolean(prop.getProperty("cosine"));
	}
	
	@Override
	public int getNumberOfFeatures() {
		return dimension + numLabels;
	}

	
	/** Vrací podobnost dokumentů vypočtenou kosínovou vzdáleností
	 * @param a - první dokument
	 * @param b - druhý dokument
	 * @return - vrací podobnost v rozmezí <0,1>, hodnota 1 značí identické dokumenty
	 */
	private double calculateCosine(List<Double> a, List<Double> b)
    {
			double dotProduct = 0.0;
	        double aMagnitude = 0.0;
	        double bMagnitude = 0.0;
	        for (int i = 0; i < b.size() ; i++) {
	            double aValue = a.get(i);
	            double bValue = b.get(i);
	            aMagnitude += aValue * aValue;
	            bMagnitude += bValue * bValue;
	            dotProduct += aValue * bValue;
	        }
	        aMagnitude = Math.sqrt(aMagnitude);
	        bMagnitude = Math.sqrt(bMagnitude);
	        return (aMagnitude == 0 || bMagnitude == 0)
	            ? 0
	            : dotProduct / (aMagnitude * bMagnitude);
		
    }
	
	
	/** Vrací vzdálenost mezi dokumenty vypočtenou euklidovskou vzdáleností
	 * @param array1
	 * @param array2
	 * @return
	 */
	private double calculateDistance(List<Double> a, List<Double> b)
    {
        double Sum = 0.0;
        for(int i=0;i<a.size();i++) {
           Sum = Sum + Math.pow((a.get(i)-b.get(i)),2.0);
        }
        return Math.sqrt(Sum);
    }
	
	
	/* (non-Javadoc)
	 * @see cz.zcu.fav.liks.ml.features.Feature#extractFeature(cz.zcu.fav.liks.ml.lists.ListIterator, cz.zcu.fav.liks.ml.features.FeatureVectorGenerator)
	 */
	@Override
	public void extractFeature(ListIterator<List<Double>> it, FeatureVectorGenerator generator) {
		List<Double> currentPoint = it.getRelative(0);		
		int best = 0;
		double bestValue = 0;
		double value;
		int i=0;
		for(int j = 0 ; j < numLabels ; j++){
			for(List<Double> tmp : docs.get(j)) {
				if (cosine) {
					value = calculateCosine(tmp, currentPoint);
				}
				else {
					value = calculateDistance(tmp, currentPoint);
				}
					
				generator.setFeature(i++, value);
				if (value > bestValue) {
					bestValue = value;
					best = j;
				}
			}	
		}
		generator.setFeature(docs.size() + best, 1);

	}

	/* (non-Javadoc)
	 * @see cz.zcu.fav.liks.ml.features.Feature#train(cz.zcu.fav.liks.ml.lists.TrainingInstanceList)
	 */
	public void train(TrainingInstanceList<List<Double>> instances) {
		numLabels = instances.getLabelCount();
		docs = new ArrayList<List<List<Double>>>();
		
		for (int i=0; i< numLabels; i++) {
			docs.add(new ArrayList<List<Double>>());
		}
		
		
		TrainingListIterator<List<Double>> iterator = instances.iterator();
		while(iterator.hasNext()){
			List<Double> currentPoint = iterator.next();
			int label = iterator.getCurrentLabel();
			docs.get(label).add(currentPoint);		
		}
	}

}
