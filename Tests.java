import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.jsoup.Jsoup;

import cz.zcu.fav.liks.math.la.DoubleMatrix;
import cz.zcu.fav.liks.ml.classify.BasicClassificationResults;
import cz.zcu.fav.liks.ml.classify.ClassificationResults;
import cz.zcu.fav.liks.ml.classify.Classifier;
import cz.zcu.fav.liks.ml.features.FeatureSet;
import cz.zcu.fav.liks.ml.lists.BasicInstanceList;
import cz.zcu.fav.liks.ml.lists.BasicTrainingInstanceList;
import documents.Document;
import documents.MethodsForDocuments;
import dp.classification.CalculatedMeasure;
import dp.classification.LDAFeature;
import dp.classification.WordFeature;



/**
 * @author Ladislav Hlom
 * Třídy sloužící pro spouštění klasifikačních testů.
 */
public class Tests {

	private ArrayList<Document> documents;					// vsechny nactene dokumenty
	private ArrayList<Document> documentsForTraining;		// dokumenty pro trenovani
	private ArrayList<Document> documentsForTesting;		// dokumenty pro testovani
	private HashMap<String, Integer> categories;			// vsechny kategorie dokumentu
	
	private List<List<List<String>>> ListOftrainingData = new ArrayList<List<List<String>>>();			// trenovaci data k trenovani
	private List<Integer> labels = new ArrayList<Integer>();											// kategorie pro trenovaci data k trenovani
	private HashMap<String, Integer> categoriesCount;												// pocet vyskytu dokumentu danne kategorie
	private HashMap<Integer, String> categoriesNames = new HashMap<Integer, String>();				// mapa pro ziskani kategorie podle indexu
	private List<List<String>> testData = new ArrayList<List<String>>();	
	
	private ArrayList<String> categoriesAfterMapping = new ArrayList<String>(); // kategorie, ktere odstranit pri mapovani na jinou kolekci
	
	private String classifierIdent = "";				// identifikace souboru podle casti dat kterou vyhodnocuji
	
	String path;							// nastavena cesta pro testovani
	private  int MIN_DOCS_FOR_TRAINING;		// minimalni mnozstvi dokumentu k trenovani pro kategorii (ma li mene, je vyhozena)
	private  int DIVIDE_SIZE;					// pocet casti pro rozdeleni dokumentu (1 cast pro testovani, zbytek pro trenovani)
		
	private ParallelLDA parallelLDA = null;		// LDA
	private Properties prop;					// konfiguracni soubor
	private String classificatorType;			// typ klasifikatoru SVM, ME ..
	
	private MethodsForDocuments docsMethods;		//obsahuje metody pro nacitani a ukladani dokumentu a klasifikatoru 
	private boolean threshold_only_best = false;
	
	
	/**
	 * Nahrání konfiguračního souboru
	 */
	private void loadConfig() {
		prop = new Properties();
		InputStream input = null;

		try {
			input = new FileInputStream("config.properties");
			// load a properties file
			prop.load(input);
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (prop.getProperty("path") != null) 
			this.path = prop.getProperty("path");
		if (prop.getProperty("MIN_DOCS_FOR_TRAINING") != null) 
			this.MIN_DOCS_FOR_TRAINING = Integer.parseInt(prop.getProperty("MIN_DOCS_FOR_TRAINING"));
		if (prop.getProperty("DIVIDE_SIZE") != null) 
			this.DIVIDE_SIZE = Integer.parseInt(prop.getProperty("DIVIDE_SIZE"));
		if (prop.getProperty("classificatorType") != null) 
			this.classificatorType = prop.getProperty("classificatorType");
		if (prop.getProperty("threshold_only_best") != null) 
			this.threshold_only_best = Boolean.parseBoolean(prop.getProperty("threshold_only_best"));
	}
	
	
	/**
	 * Kontruktor načítá konfigurační soubor a inicializuje třídu pro práci s dokumenty
	 */
	public Tests() {
		loadConfig();
		docsMethods = new MethodsForDocuments();
	}
	
	
	/**
	 *  Odstranění html elementů z textu
	 * @param html - Text k uprávě
	 * @return - Upravený text bez html elementů
	 */
	public String html2text(String html) {
	    return Jsoup.parse(html).text();
	}
		
	
	
	/**
	 * Vytvoření trénovacích dat pro klasifikaci binárním sjednocením
	 * @param firstCategory - kategorie pro kterou je binární klasifikátor vytvářen
	 */
	void createTrainingDataForClass(String firstCategory) {
		ListOftrainingData = new ArrayList<List<List<String>>>();
		
		// binarni klasifikator ma dve kategorie
		for (int i=0; i < 2; i++) {
			ListOftrainingData.add(new ArrayList<List<String>>());
			ListOftrainingData.get(i).add(new ArrayList<String>());
		}
		
		// ulozeni retezcu do trenovacich data pro vsechny trenovaci dokumenty, prvni jsou dokumenty dane kategorie, druhe vsechny ostatni		
		for (Document doc :documentsForTraining) {			
			String str;
			
			if (doc.getMessage() != null) {
				str = doc.getMessage();
				
				List<String> a = new ArrayList<String>();
				
				for (String tes : str.split(" ")) {
					a.add(tes);
				}			
				
				if (doc.getRightCategories().contains(firstCategory)) {
					ListOftrainingData.get(0).add(a);
				}
				else {
					ListOftrainingData.get(1).add(a);
				}			
			}		
		}
	}
	
	
	
	/** Spuštění klasifikace binárním sjednocením. Testy jsou spuštěny pro všechny kategorie. 
	 * @return - výsledky klasifikace
	 */
	private CalculatedMeasure testClass() {
		int k=0;	
		int numLabels = 2;
		BasicTrainingInstanceList<List<String>> trainingSet = null;	 
		FeatureSet<List<String>> set = new FeatureSet<List<String>>();
		List<List<String>> trainingData;
		
		// pro kazdou kategorii
		for(Entry<String, Integer> entry : categories.entrySet()) {
			Classifier classifier = null;
			System.out.println("Probiha klasifikace binarnim sjednocenim: kategorie - " + k + " z celkove " + categories.size());
			
			trainingData = new ArrayList<List<String>>();
			labels = new ArrayList<Integer>();
			
			createTrainingDataForClass(entry.getKey());
			
			String filename = path + "classifier" + classifierIdent + k +".ser";
			classifier = docsMethods.loadClassifier(filename);		// nacte klasifikator pokud jiz byl natrenovan
			
					
			// trenovaci data pro kategorii
			for (List<String> document: ListOftrainingData.get(0)) {
				trainingData.add(document);
				labels.add(0);	
			}
			
			for (List<String> document: ListOftrainingData.get(1)) {
				trainingData.add(document);
				labels.add(1);	
			}
				
			trainingSet = new BasicTrainingInstanceList<List<String>>(trainingData, null, labels, numLabels);
			
			// vytvoreni priznaku
			set = new FeatureSet<List<String>>();
			set.add(new WordFeature(0));
			set.train(trainingSet);
				
			if (classifier == null) {
				classifier = docsMethods.createClassifier(numLabels, trainingSet, set, classificatorType);
			}
				
			// vytvoreni testovacich dat
			BasicInstanceList<List<String>> testSet = new BasicInstanceList<List<String>>(testData, null);
			// vytvoreni vektoru priznaku
			DoubleMatrix data = set.getData(testSet);
			
			// klasifikace vektoru priznaku
			ClassificationResults results = BasicClassificationResults.create(numLabels, testSet.size());
			classifier.classify(data, results);
			
			// vyhodnoceni vysledku
			DoubleMatrix probabilities = results.getProbabilities();		
			
			for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
				if (probabilities.get(0, ci) > probabilities.get(1, ci)) {
					documentsForTesting.get(ci).addToCategory(entry.getKey());
				}
			}
				
			k++;
			docsMethods.saveClassifier(filename, classifier);		
		}
				
		return MethodsForDocuments.writeResults(path  +"vypis//vypis" + classifierIdent +  ".txt", documentsForTesting);
	}
	
	

	/** Spuštění klasifikace prahováním. 
	 * @param threshold_value - hodnota prahovací meze
	 * @return výsledky klasifikace
	 */
	private CalculatedMeasure testThreshold(double threshold_value) {
		System.out.println("Probiha klasifikace prahovanim");
		int numLabels = categories.size();
		Classifier classifier = null;
		List<List<String>> trainingData = new ArrayList<List<String>>(); 
		int k= 0;

		for (List<List<String>> documentsList:ListOftrainingData) {	
			for (List<String> document: documentsList) {
				trainingData.add(document);
				labels.add(k);	
			}
			k++;
		}	
						
		String filename = path  + "classifier_threeshold" + classifierIdent + ".ser";
		classifier = docsMethods.loadClassifier(filename);		// nacte klasifikator pokud jiz byl natrenovan
						
		// vytvoreni trenovacich dat
		BasicTrainingInstanceList<List<String>> trainingSet = new BasicTrainingInstanceList<List<String>>(trainingData, null, labels, numLabels);
		// vytvoreni priznaku
		FeatureSet<List<String>> set = new FeatureSet<List<String>>();
		set.add(new WordFeature(0));
		set.train(trainingSet);
			
		if (classifier == null) {
			classifier = docsMethods.createClassifier(numLabels, trainingSet, set, classificatorType);
		}
	
		// vytvoreni testovacich dat
		BasicInstanceList<List<String>> testSet = new BasicInstanceList<List<String>>(testData, null);
		// vytvoreni vektoru priznaku
		DoubleMatrix data = set.getData(testSet);
		// klasifikace vektoru priznaku
		ClassificationResults results = BasicClassificationResults.create(numLabels, testSet.size());
		classifier.classify(data, results);
			
		docsMethods.saveClassifier(filename, classifier);	
		
		// vyhodnoceni vysledku
		DoubleMatrix probabilities = results.getProbabilities();
			
		if (threshold_only_best) {						// klasifikuje se jen do jedne kategorie - bere se nejlepsi vysledek
			for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
				int best = -1;
				double bestVal = 0;
				for (int j=0; j < numLabels; j++) {
					if (probabilities.get(j, ci) > bestVal) {
						best = j;
						bestVal = probabilities.get(j, ci);
					}
				}
				documentsForTesting.get(ci).addToCategory(categoriesNames.get(best));
			}
		}
		else {												// klasifikuje se  do vice kategorii - zaradi se pokud je vetsi nez prahovaci mez
			for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
				for (int j=0; j < numLabels; j++) {
					if (probabilities.get(j, ci) > threshold_value) {
						documentsForTesting.get(ci).addToCategory(categoriesNames.get(j));
					}
				}
			}
		}
		return MethodsForDocuments.writeResults(path  +"vypis//vypis_threashodl" + classifierIdent +  ".txt", documentsForTesting);			
	}
		
	
	
	
	
	/**
	 * Vytvoření trénovacích dat pro klasifikaci binárním sjednocením za použití metody LDA.
	 * @param firstCategory - kategorie pro kterou je binární klasifikátor vytvářen
	 */
	void createTrainingDataForClassLDA(String firstCategory) {
		ListOftrainingData = new ArrayList<List<List<String>>>();
		
		// binarni klasifikator ma dve kategorie
		for (int i=0; i < 2; i++) {
			ListOftrainingData.add(new ArrayList<List<String>>());
			ListOftrainingData.get(i).add(new ArrayList<String>());
		}
		
		// ulozeni retezcu do trenovacich data pro vsechny trenovaci dokumenty		
		for (Document doc :documentsForTraining) {			
			String str;
			
			if (doc.getMessage() != null) {
				str = doc.getMessage();
				
				List<String> a = new ArrayList<String>();
				
				for (String tes : str.split(" ")) {
					a.add(tes);
				}			
				
				if (doc.getRightCategories().contains(firstCategory)) {
					ListOftrainingData.get(0).add(a);
				}
				else {
					ListOftrainingData.get(1).add(a);
				}			
			}		
		}
	}
	
	
	
	/** Klasifikace binárním sjednocením za použití metody LDA
	 * @return výsledky klasifikace
	 */
	private CalculatedMeasure testLDA() {
		System.out.println("Probiha klasifikace binarnim sjednocenim za pouziti metody lda");
		String agenture = "";
		boolean loadTranslated = Boolean.parseBoolean(prop.getProperty("loadTranslated"));
		boolean	translate = Boolean.parseBoolean(prop.getProperty("translate"));	
		boolean	testsForColection = Boolean.parseBoolean(prop.getProperty("testsForOneCollection"));		// kolekce namapovana z anglictiny
		

		int k=0;	
		int numLabels = 2;
		BasicTrainingInstanceList<List<Double>> trainingSet = null;	 
		FeatureSet<List<Double>> set = new FeatureSet<List<Double>>();
		LDAmethods ldaMethods = new LDAmethods();
		
		if (!testsForColection) {		
			agenture = "ap";
		}	
		
		// pro kazdou kategorii
		for(Entry<String, Integer> entry : categories.entrySet()) {
			Classifier classifier = null;
			System.out.println("Probiha klasifikace binarnim sjednocenim za pouziti metody lda: kategorie - " + k + " z celkove " + categories.size());
			
			ArrayList<List<Double>> trainingData2 = new ArrayList<List<Double>>();
			labels = new ArrayList<Integer>();
			
			createTrainingDataForClassLDA(entry.getKey());
					
			List<List<Double>> testData2 = new ArrayList<List<Double>>();
			for (List<String> tmp: testData) {
				String[] stockArr = ldaMethods.prepareDocument(tmp, agenture);	
				List<Double> vector = ldaMethods.getVector(parallelLDA, stockArr);
				testData2.add(vector);
			}
			
			String filename = path + "classifierLDA" + classifierIdent + k +".ser";
			classifier = docsMethods.loadClassifier(filename);		// nacte klasifikator pokud jiz byl natrenovan
			
			
			if (!testsForColection) {
				agenture = "";
			}
			else if (!loadTranslated && translate) {			// prelozene dokumenty nenacteny, pro testovani a trenovani jsou tak anglicke dokumenty
				agenture = "ap";
			}
			
			// trenovaci data pro kategorii
			for (List<String> document: ListOftrainingData.get(0)) {
				String[] stockArr = ldaMethods.prepareDocument(document, agenture);	
				List<Double> vector = ldaMethods.getVector(parallelLDA, stockArr);
				trainingData2.add(vector);
				labels.add(0);	
			}
			
			for (List<String> document: ListOftrainingData.get(1)) {
				String[] stockArr = ldaMethods.prepareDocument(document, agenture);	
				List<Double> vector = ldaMethods.getVector(parallelLDA, stockArr);
				trainingData2.add(vector);
				labels.add(1);	
			}
				
			// vytvoreni trenovacich dat
			trainingSet = new BasicTrainingInstanceList<List<Double>>(trainingData2, null, labels, numLabels);
			// vytvoreni priznaku
			set = new FeatureSet<List<Double>>();
			set.add(new LDAFeature(documentsForTraining.size(), prop));
			set.train(trainingSet);
			
			if (classifier == null) {
				classifier = docsMethods.createClassifier(numLabels, trainingSet, set, classificatorType);
			}
				
			// vytvoreni testovacich dat
			BasicInstanceList<List<Double>> testSet = new BasicInstanceList<List<Double>>(testData2, null);
			// vytvoreni vektoru priznaku
			DoubleMatrix data = set.getData(testSet);
			
			// klasifikace vektoru priznaku
			ClassificationResults results = BasicClassificationResults.create(numLabels, testSet.size());
			classifier.classify(data, results);
			
			// vyhodnoceni vysledku
			DoubleMatrix probabilities = results.getProbabilities();		
			
			for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
				if (probabilities.get(0, ci) > probabilities.get(1, ci)) {
					documentsForTesting.get(ci).addToCategory(entry.getKey());
				}
			}
				
			k++;
			docsMethods.saveClassifier(filename, classifier);		
		}
				
		return MethodsForDocuments.writeResults(path  +"vypis//vypis" + classifierIdent +  ".txt", documentsForTesting);
	}
	
	
	/**
	 * Klasifikace prahováním za použití metody LDA.
	 * @param threshold_value - hodnota meze prahování
	 * @return výsledky klasifikace
	 */
	private CalculatedMeasure testThresholdLDA(double threshold_value) {
		System.out.println("Probiha klasifikace prahovanim za pouziti metody lda" );
		String agenture = "";
		boolean loadTranslated = Boolean.parseBoolean(prop.getProperty("loadTranslated"));
		boolean	translate = Boolean.parseBoolean(prop.getProperty("translate"));
		boolean	testsForColection = Boolean.parseBoolean(prop.getProperty("testsForOneCollection"));		// kolekce namapovana z anglictiny
			
		if (!testsForColection) {	// pro trenovani jsou pouzity ceske dokumenty
			agenture = "";
		}
		else if (!loadTranslated && translate) {
			agenture = "ap";
		}
		
		LDAmethods ldaMethods = new LDAmethods();
		
		int numLabels = categories.size();
		Classifier classifier = null;
		int k= 0;

		ArrayList<List<Double>> trainingData2 = new ArrayList<List<Double>>();
		
		for (List<List<String>> documentsList:ListOftrainingData) {	
			for (List<String> document: documentsList) {
				
				String[] stockArr = ldaMethods.prepareDocument(document, agenture);	
				List<Double> vector = ldaMethods.getVector(parallelLDA, stockArr);
				trainingData2.add(vector);
				labels.add(k);	
			}
			k++;
		}	
						
		
		if (!testsForColection) {		// pro trenovani jsou pouzity ceske dokumenty
			agenture = "ap";
		}	
		
		List<List<Double>> testData2 = new ArrayList<List<Double>>();
		for (List<String> tmp: testData) {
			String[] stockArr = ldaMethods.prepareDocument(tmp, agenture);	
			List<Double> vector = ldaMethods.getVector(parallelLDA, stockArr);
			testData2.add(vector);
		}
		
		
		
		String filename = path  + "classifier_threeshold" + classifierIdent + ".ser";
		classifier = docsMethods.loadClassifier(filename);		// nacte klasifikator pokud jiz byl natrenovan
						
		// vytvoreni trenovacich dat
		BasicTrainingInstanceList<List<Double>> trainingSet = new BasicTrainingInstanceList<List<Double>>(trainingData2, null, labels, numLabels);
		// vytvoreni priznaku
		FeatureSet<List<Double>> set = new FeatureSet<List<Double>>();
		set.add(new LDAFeature(trainingData2.size(), prop));
		set.train(trainingSet);
			
		if (classifier == null) {
			classifier = docsMethods.createClassifier(numLabels, trainingSet, set, classificatorType);
		}
	
		// vytvoreni trenovacich dat
		BasicInstanceList<List<Double>> testSet = new BasicInstanceList<List<Double>>(testData2, null);
		// vytvoreni vektoru priznaku
		DoubleMatrix data = set.getData(testSet);
		// klasifikace vektoru priznaku
		ClassificationResults results = BasicClassificationResults.create(numLabels, testSet.size());
		classifier.classify(data, results);
			
		docsMethods.saveClassifier(filename, classifier);	
		
		// vyhodnoceni vysledku
		DoubleMatrix probabilities = results.getProbabilities();
		
		if (threshold_only_best) {			// klasifikuje se  do jedne kategorie - bere se nejlepsi vysledek
			
			for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
				int best = -1;
				double bestVal = 0;
				
				for (int j=0; j < numLabels; j++) {
					
					if (probabilities.get(j, ci) > bestVal) {
						best = j;
						bestVal = probabilities.get(j, ci);
					}
				}
				documentsForTesting.get(ci).addToCategory(categoriesNames.get(best));
			}
		}
		else {				// klasifikuje se  do vice kategorii - zaradi se pokud je vetsi nez prahovaci mez
			for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
				for (int j=0; j < numLabels; j++) {
					if (probabilities.get(j, ci) > threshold_value) {
						documentsForTesting.get(ci).addToCategory(categoriesNames.get(j));
					}
				}
			}
		}
		
		return MethodsForDocuments.writeResults(path  +"vypis//vypis_threashodl" + classifierIdent +  ".txt", documentsForTesting);			
	}
	
	
	/**
	 * Zjištění všech kategorií v tréninkových dokumentech s počtem výskytu.
	 */
	void categoriesPreparation() {
		categories = new HashMap<>();
		categoriesCount = new HashMap<>();
		int categoryIndex = 0;
		
		// pres vsechny treninkove dokumenty
		for (Document doc : documentsForTraining) {		
			for (String category : doc.getRightCategories()) {
				
				if (!categories.containsKey(category)) {
					categories.put(category, categoryIndex++);
					categoriesCount.put(category, 1);
				}
				else {		
					categoriesCount.put(category, categoriesCount.get(category) + 1);
				}	
			}
		}
	}
	

	/**
	 *  Vyhodí kategorie, které obsahují málo trénovacích dokumentů. Vrací seznam kategorií, které je nutné odstranit
	 * @return - seznam kategorií k odstranění
	 */
	ArrayList<String> removeCategoriesWithLowOccurences() {
		ArrayList<String> categoriesToRemove = new ArrayList<String>();
		
		// pres vsechny kategorie
		for(Entry<String, Integer> entry : categoriesCount.entrySet()) {
		    String category = entry.getKey();
		    Integer categoryOccurences = entry.getValue();
		    
		    if (categoryOccurences < MIN_DOCS_FOR_TRAINING) {
		    	System.out.println("vyhazuje se " + category + " dokuemtn " + categoryOccurences );
		    	categoriesToRemove.add(category);
		    }
		}
		
		// odstrani z mapy kategorie s malo vyskyty
		for(String entry : categoriesToRemove) {
			categories.remove(entry);
		}
		return categoriesToRemove;
	}
	
	
	// odstraneni kategorii s malo vyskyty z dokumentu
	
	/** Odstraní dokumenty z trénovacích a testovacích dokumentů, které se vyskytují v kategoriích k odstranění. 
	 * @param categoriesToRemove - kategorie k odstranění
	 */
	void removeCategoriesFromDocuments(ArrayList<String> categoriesToRemove) {
		
		// odstraneni kategorii z testovacich dokumentu
		Iterator<Document> iter = documentsForTesting.iterator();
		while (iter.hasNext()) {
			Document tmp = iter.next();
			
			for (Iterator<String> it = tmp.getRightCategories().iterator(); it.hasNext();) {
				String cat = it.next();
				if (categoriesToRemove.contains(cat)) {
					it.remove();
				}
			}
			// nezbyla zadna kategorie, vyhodime cely dokument				
			if (tmp.getRightCategories().isEmpty()) {
				iter.remove();
			}	
			
		}
		
		// odstraneni kategorii z trenovacich dokumentu
		iter = documentsForTraining.iterator();
		while (iter.hasNext()) {
			Document tmp = iter.next();
			for (Iterator<String> it = tmp.getRightCategories().iterator(); it.hasNext();) {
				String cat = it.next();
				if (categoriesToRemove.contains(cat)) {
					it.remove();
				}
			}
						
			if (tmp.getRightCategories().isEmpty()) {
				iter.remove();
			}	
		}
	}
	
	
	/**
	 * Vytvoření trénovacích dat 
	 */
	void createTrainingData() {
		testData = new ArrayList<List<String>>();
		ListOftrainingData = new ArrayList<List<List<String>>>();
		categoriesNames = new HashMap<Integer, String>();
		labels = new ArrayList<Integer>();
		
		
		// odstraneni html tagu a znaku ze zpravy, ulozeni do testovacich dat
		String message;
		int i = 0;
		for (Document doc: documentsForTesting) {
			doc.setMessage(html2text(doc.getMessage().replaceAll("([.,:-=;'/\\/\"])", " $1 ")));
			
			
			testData.add(new ArrayList<String>());	
			message = doc.getMessage();
			for (String s :message.split(" ")) {
				testData.get(i).add(s);
			}
			i++;
		}
		
		//pro kazdou kategorii vytvorit pole trenovacich data
		for (i=0; i < categories.size(); i++) {
			ListOftrainingData.add(new ArrayList<List<String>>());
			ListOftrainingData.get(i).add(new ArrayList<String>());
		}

		// vytvoreni mapy pro zjisteni kategorie podle indexu
		i = 0;
		for(Entry<String, Integer> entry : categories.entrySet()) {
		    String key = entry.getKey();
		    categoriesNames.put(i, key);
		    entry.setValue(i++);	    
		}
		
		// ulozeni retezcu do trenovacich data pro vsechny trenovaci dokumenty		
		for (Document doc :documentsForTraining) {		
			int index = 0;	
			String str;
			
			if (doc.getMessage() != null) {
				doc.setMessage(html2text(doc.getMessage().replaceAll("([.,:-=;'/\\/\"])", " $1 ")));

				str = doc.getMessage();
				
				for (String category : doc.getRightCategories()) {
					
					index = categories.get(category);
								
					List<String> a = new ArrayList<String>();
					
					for (String tes : str.split(" ")) {
						a.add(tes);
					}				
					ListOftrainingData.get(index).add(a);
				}
			}		
		}
	}
	
	
	
	/** Spuštění typu klasifikace v závislosti na nastavení.
	 * @param i - proběhlá iterace, parametr důležitý při klasifikaci křižovou validací
	 * @param calculatedMeasure - struktura pro uložení výsledků
	 * @param method - zvolená metoda klasifikace (prahování, binární sjednocení, LDA)
	 * @param thresholdValue - hodnota prahovací meze
	 * @param ldaClass - zda se jedná o binární sjednocení (parametr použit pouze při metodě LDA), při false klasifikace prahováním
	 * @return - vrací strukturu s výsledky
	 * @throws IOException - soubor nenalezen
	 */
	private CalculatedMeasure runMethod(int i, CalculatedMeasure calculatedMeasure, String method, double thresholdValue,  boolean ldaClass) throws IOException {
		categoriesPreparation();
		ArrayList<String> categoriesToRemove = removeCategoriesWithLowOccurences();
		if (!categoriesToRemove.isEmpty()) {
			removeCategoriesFromDocuments(categoriesToRemove);
		}
		
		boolean removeOtherCategories = false, testsForOneCollection = false;
		
		if (prop.getProperty("removeOtherCategories") != null) 
			removeOtherCategories = Boolean.parseBoolean(prop.getProperty("removeOtherCategories"));
		if (prop.getProperty("testsForOneCollection") != null) 
			testsForOneCollection = Boolean.parseBoolean(prop.getProperty("testsForOneCollection"));
		
		
		if (removeOtherCategories && !testsForOneCollection) {
			ArrayList<String> list = new ArrayList<String>(categories.keySet());
			list.removeAll(categoriesAfterMapping);
			removeCategoriesFromDocuments(list);
			categoriesPreparation();
		}
		
		
		createTrainingData();
		classifierIdent = "(" + i  +  ")";	// k identifikaci klasifikatoru podle casti testovanych dat
		
		
		// vybere metodu podle parametru
		if (method.equals("threshold")) {
			calculatedMeasure = testThreshold(thresholdValue);
		}	
		else if (method.equals("class")){
			calculatedMeasure = testClass();
		}
		else if (method.equals("lda")) {
			try {
				if (i == 1 && parallelLDA == null) {
					parallelLDA = ParallelLDA.load(prop.getProperty("ldaFile"));
				}
				if (ldaClass) {
					calculatedMeasure = testLDA();
				}
				else {
					calculatedMeasure = testThresholdLDA(thresholdValue);
				}	
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		return calculatedMeasure;
	}

	
	
	
	/**
	 * Pouští testy pro jednu kolekci 
	 * @param writer - soubor kam budou zapsány výsledky
	 * @throws IOException - soubor nenalezen
	 */
	private void  testsForCollection(PrintWriter writer) throws IOException {
		
		// nahrani konfiguracnich dat
		String  method = "", collection="";
		boolean  loadTranslated = false, translate = false, ldaClass = false, google=false;
		double thresholdValue = 0;	

		
		if (prop.getProperty("translate") != null) 
			translate = Boolean.parseBoolean(prop.getProperty("translate"));
		if (prop.getProperty("loadTranslated") != null) 
			loadTranslated = Boolean.parseBoolean(prop.getProperty("loadTranslated"));
		if (prop.getProperty("google") != null) 
			google = Boolean.parseBoolean(prop.getProperty("google"));
		if (prop.getProperty("method") != null) 
			method = prop.getProperty("method");

		if (prop.getProperty("ldaClass") != null) 
			ldaClass = Boolean.parseBoolean(prop.getProperty("ldaClass"));
		if (prop.getProperty("collection") != null) 
			collection = prop.getProperty("collection");
		if (prop.getProperty("thresholdValue") != null) 
			thresholdValue = Double.parseDouble(prop.getProperty("thresholdValue"));
		
			
		CalculatedMeasure calculatedMeasure = null;
		double precisionSum = 0;
		double recalSuml = 0;
		double fMeasureSum = 0;
		
		if (collection.equals("ctk")) {		// testy pro kolekci od ctk (bud ceske dokumenty nebo anglicke z xml souboru)
			
			// data jsou rozdelena na nekolik casti (krizova validace)
			for (int i = 1; i <= DIVIDE_SIZE; i++) {
				
				if (translate) {		// nacitame prelozene soubory
									
					if (loadTranslated) {		// pokud nenacitame prelozene dokumenty, musi se pridat do documents 
						documents = docsMethods.loadXMLData("collections/ctk/anglicke/export-all.xml");
					}
					else {
						documents = docsMethods.loadXMLData("collections/ctk/anglicke/export-all.xml");
						docsMethods.statisticsForDocument(documents);	
					}
					
					if (loadTranslated) {
						if (google) {
							documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/google.txt", documents,false);  // todo testovani pro lda
						}
						else {
							documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/moses.txt", documents,false);  // todo testovani pro lda
						}			
					}
					else {		
						if (google) {
							documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/google.txt", documents,true);  // vyhodi se dokumenty, ktere nebyly v prelozenem souboru
						}
						else {
							documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/moses.txt", documents,true); // vyhodi se dokumenty, ktere nebyly v prelozenem souboru
						}	
					}
					
				}
				else {					// nacitame neprelozene soubory
					documents = docsMethods.loadAllDocuments("collections/ctk/ceske/czech_text_document_corpus_v10");
					docsMethods.statisticsForDocument(documents);			
				}
				
				int size = documents.size() / DIVIDE_SIZE;
				System.out.println("delka kolekce " + documents.size());
				
				if (i == 1) {
					documentsForTesting = new ArrayList<Document>(documents.subList(0, size));
					documentsForTraining =  new ArrayList<Document>(documents.subList(size, documents.size()));
				}
				else if (i == DIVIDE_SIZE) {
					documentsForTraining= new ArrayList<Document>(documents.subList(0, documents.size() - size));
					documentsForTesting =  new ArrayList<Document>(documents.subList(documents.size() - size, documents.size()));
				}
				else {
					documentsForTraining = new ArrayList<Document>(documents.subList(0, size * (i - 1)));
					documentsForTraining.addAll(documents.subList(size * i, documents.size()));
					documentsForTesting =  new ArrayList<Document>(documents.subList(size * (i - 1), size * i));
				}
				
				
				if (loadTranslated && method.equals("lda")) {
					for (Document doc :documentsForTraining) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
						doc.setAgenture("");
					}
					for (Document doc :documentsForTesting) {		
						doc.setAgenture("");
					}
				}
							
				calculatedMeasure = runMethod(i, calculatedMeasure, method, thresholdValue,  ldaClass);
	
				precisionSum += calculatedMeasure.precision;
				recalSuml += calculatedMeasure.recall;
				fMeasureSum += calculatedMeasure.fMeasure;	
					
			}
		}
		else if (collection.equals("reuters")) {			// testy pro reuters kolekci
							
			if (loadTranslated) {				
				if (google) {		// google preklada do jednoho souboru
					documentsForTraining = docsMethods.loadDocumentsFromFile("engTraining.txt","collections/reuters/Reuters90Cat/training/");
					documentsForTraining = docsMethods.loadTranslatedDocuments("collections/reuters/trainingReutersGoogle.txt", documentsForTraining, true);  
					
					documentsForTesting = docsMethods.loadDocumentsFromFile("engTest.txt","collections/reuters/Reuters90Cat/test/");						
					documentsForTesting = docsMethods.loadTranslatedDocuments("collections/reuters/testovaciReutersGoogle.txt", documentsForTesting ,true);  
				}
				else {			// moses preklada jednotlive soubory
					documentsForTraining = docsMethods.loadDocumentsFromFile("engTraining.txt","collections/reuters/Reuters90CatTranslated/training/");		
					documentsForTesting = docsMethods.loadDocumentsFromFile("engTest.txt","collections/reuters/Reuters90CatTranslated/test/");
				}			
			}
			else {
				documentsForTraining = docsMethods.loadDocumentsFromFile("engTraining.txt","collections/reuters/Reuters90Cat/training/");
				documentsForTesting = docsMethods.loadDocumentsFromFile("engTest.txt","collections/reuters/Reuters90Cat/test/");
			}
			
			documents = new ArrayList<Document>(documentsForTraining);
			documents.addAll(documentsForTesting);
			docsMethods.statisticsForDocument(documents);
	
			if (loadTranslated && method.equals("lda")) {
				for (Document doc :documentsForTraining) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
				for (Document doc :documentsForTesting) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
			}
				
			calculatedMeasure = runMethod(1, calculatedMeasure, method, thresholdValue, ldaClass);
			
			DIVIDE_SIZE = 1;
			precisionSum += calculatedMeasure.precision;
			recalSuml += calculatedMeasure.recall;
			fMeasureSum += calculatedMeasure.fMeasure;	
		}
		else if (collection.equals("bbc")) {		// testy pro kolekci od bbc
			
			if (loadTranslated) {
				documents= docsMethods.loadDocumentsFromFile("engBBC.txt","collections/bbc/bbc/");
				if (google) {
					documents = docsMethods.loadTranslatedDocuments("collections/bbc/BBCGoogle.txt", documents, false); 
				}
				else {
					documents = docsMethods.loadDocumentsFromFile("engBBC.txt","collections/bbc/bbcTranslated/");
				}
			}
			else {
				documents = docsMethods.loadDocumentsFromFile("engBBC.txt","collections/bbc/bbc/");
			}
			
			docsMethods.statisticsForDocument(documents);
	
			if (loadTranslated && method.equals("lda")) {
				for (Document doc :documentsForTraining) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
				for (Document doc :documentsForTesting) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
			}
				
			calculatedMeasure = runMethod(1, calculatedMeasure, method, thresholdValue,  ldaClass);
			
			DIVIDE_SIZE = 1;
			precisionSum += calculatedMeasure.precision;
			recalSuml += calculatedMeasure.recall;
			fMeasureSum += calculatedMeasure.fMeasure;	
		}
		

		writer.println("Pocet kategorii " + categories.size());
		writer.println("Vypocteny precision" + (double)precisionSum/DIVIDE_SIZE);
		writer.println("Vypocteny recal" + (double)recalSuml/DIVIDE_SIZE);
		writer.println("Vypoctena f measure" + (double)fMeasureSum/DIVIDE_SIZE);
		
	}
	
	
	
	
	/** Přemapuje dokumenty do daných kategorií
	 * @param docs - dokumenty, které mají být přemapovány
	 * @param mapping - mapa obsahující jak mají být kategorie přemapovány (klíč - kategorie, hodnota - přemapovaná kategorie)
	 * @return - přemapované dokumenty
	 */
	private ArrayList<Document> mapCategoriesForCollections(ArrayList<Document> docs, HashMap<String, String[]> mapping) {
		
		String[] tmpCategory;
		Iterator<Document> iter = docs.iterator();
		while (iter.hasNext()) {											// vsechny dokumenty
			ArrayList<String> tmpCategories = new ArrayList<String>();		// premapovane kategorie dokumentu
			Document doc = iter.next();
			for (String category : doc.getRightCategories()) {			// pro vsechny kategorie
				tmpCategory = mapping.get(category);
				if (tmpCategory != null) {								// dana kategorie nemuze byt premapovana
					for (String mappedCategory : tmpCategory) {			// pridej je do premapovanych kategorii
						tmpCategories.add(mappedCategory);
					}
				}		
			}
			
			if (tmpCategories.isEmpty()) {						// pro dokument nebyly premapovany zadne kategorie, musi byt odstranen
				iter.remove();
			}
			else {												// nastav dokumentu nove kategorie
				doc.setRightCategories(tmpCategories);
			}
			
		}
		return docs;
	}
	
	
	/** Testy pro přemapované kolekce ve dvou jazycích - model je natrénován na dokumentech v jednom jazyce, zatímco testovací dokumenty jsou v jiném
	 * @param writer - soubor kam budou zapsány výsledky
	 * @throws IOException - soubor nenalezen
	 */
	private void  testsForBothCollections(PrintWriter writer) throws IOException {
		
		CalculatedMeasure calculatedMeasure = null;
		double precisionSum = 0;
		double recalSuml = 0;
		double fMeasureSum = 0;
		
		// nahrani konfiguracniho souboru
		String method = "", collection="";
		boolean  loadTranslated = false, translate = false, ldaClass = false, google =false;
		double thresholdValue = 0;	

		if (prop.getProperty("google") != null) 
			google = Boolean.parseBoolean(prop.getProperty("google"));
		if (prop.getProperty("translate") != null) 
			translate = Boolean.parseBoolean(prop.getProperty("translate"));
		if (prop.getProperty("loadTranslated") != null) 
			loadTranslated = Boolean.parseBoolean(prop.getProperty("loadTranslated"));
		if (prop.getProperty("method") != null) 
			method = prop.getProperty("method");
		if (prop.getProperty("ldaClass") != null) 
			ldaClass = Boolean.parseBoolean(prop.getProperty("ldaClass"));
		if (prop.getProperty("collection") != null) 
			collection = prop.getProperty("collection");
		if (prop.getProperty("thresholdValue") != null) 
			thresholdValue = Double.parseDouble(prop.getProperty("thresholdValue"));
		
		
		
		documentsForTraining = docsMethods.loadAllDocuments("collections/ctk/ceske/czech_text_document_corpus_v10");		// cela ceska kolekce pro trenovani
		
		if (collection.equals("ctk")) {			// testz pro anglickou kolekci od ctk
			
			// premapovani kategorii pro kolekci
			HashMap<String, String[]> mapping = new HashMap<String, String[]>();
			mapping.put("ixx", new String[] { "zah", "pol"});
			mapping.put("axx", new String[] { "zah",  "zak" });
			mapping.put("sxx", new String[] { "spo" });
			mapping.put("fxx", new String[] { "obo", "fin" });
			mapping.put("exx", new String[] { "kul", "den" });
			mapping.put("txx", new String[] { "zah", "kat" });
			
			for(Entry<String, String[]> entry : mapping.entrySet()) {
			    String[] mappedCategories = entry.getValue();
			    for (String key: mappedCategories) {
			    	categoriesAfterMapping.add(key);
			    }
			}
			
			if (translate) {
				if (loadTranslated) {		// pokud nenacitame prelozene dokumenty, musi se pridat do documents 
					documents = docsMethods.loadXMLData("collections/ctk/anglicke/export-all.xml");
				}
				else {
					documents = docsMethods.loadXMLData("collections/ctk/anglicke/export-all.xml");
				}
				
				if (loadTranslated) {
					
					if (google) {
						documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/google.txt", documents,false);  
					}
					else {
						documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/moses.txt", documents,false);  
					}			
				}
				else {
					if (google) {
						documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/google.txt", documents,true);  
					}
					else {
						documents = docsMethods.loadTranslatedDocuments("collections/ctk/anglicke/moses.txt", documents,true); 
					}			
				}		
			}
			
			documentsForTesting = mapCategoriesForCollections(documents, mapping);
	
			if (loadTranslated && method.equals("lda")) {
				for (Document doc :documentsForTraining) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
				for (Document doc :documentsForTesting) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
			}		
		} 
		else if (collection.equals("bbc")) {			// testy pro kolekci bbc
			
			// premapovani kategorii pro kolekci
			HashMap<String, String[]> mapping = new HashMap<String, String[]>();
			mapping.put("business", new String[] { "mak"});
			mapping.put("entertainment", new String[] { "kul"});
			mapping.put("politics", new String[] { "pol" });
			mapping.put("sport", new String[] { "spo" });
			mapping.put("tech", new String[] { "pit" });
			
			
			for(Entry<String, String[]> entry : mapping.entrySet()) {
			    String[] mappedCategories = entry.getValue();
			    for (String key: mappedCategories) {
			    	categoriesAfterMapping.add(key);
			    }
			}
			
			
			if (loadTranslated) {
				if (google) {
					documents = docsMethods.loadDocumentsFromFile("engBBC.txt","collections/bbc/bbc/");
					documents = docsMethods.loadTranslatedDocuments("collections/bbc/BBCGoogle.txt", documents, false);  
				}
				else {
					documents = docsMethods.loadDocumentsFromFile("engBBC.txt","collections/bbc/bbcTranslated/");
				}
							
			}
			else {
				documents = docsMethods.loadDocumentsFromFile("engBBC.txt","collections/bbc/bbc/");
			}
			
			documentsForTesting = mapCategoriesForCollections(documents, mapping);
	
			if (loadTranslated && method.equals("lda")) {
				for (Document doc :documentsForTraining) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
				for (Document doc :documentsForTesting) {		// vyhodi se agentura, protoze se zmeni na cz a tou musi byt oznackovany dokumenty pro lda
					doc.setAgenture("");
				}
			}		
		}
		
		
		calculatedMeasure = runMethod(1, calculatedMeasure, method, thresholdValue,  ldaClass);

		precisionSum += calculatedMeasure.precision;
		recalSuml += calculatedMeasure.recall;
		fMeasureSum += calculatedMeasure.fMeasure;	
			
		
		writer.println("Pocet kategorii " + categories.size());
		writer.println("Vypocteny precision" + (double)precisionSum);
		writer.println("Vypocteny recal" + (double)recalSuml);
		writer.println("Vypoctena f measure" + (double)fMeasureSum);	
	}
	
	
	/**
	 * Spouští celý proces testování
	 * @throws IOException - soubor nenalezen
	 */
	public void run() throws IOException {
		BasicConfigurator.configure();
		
		PrintWriter writer = null;	
		boolean testsForOneCollection = false;
		if (prop.getProperty("testsForOneCollection") != null) 
			testsForOneCollection = Boolean.parseBoolean(prop.getProperty("testsForOneCollection"));
		
		writer = new PrintWriter(path + "vypis_hotovo.txt", "UTF-8");
		
		if (testsForOneCollection) {
			testsForCollection(writer);
		}
		else {
			testsForBothCollections(writer);
		}	
		
		writer.flush();
		writer.close();		
	}
}