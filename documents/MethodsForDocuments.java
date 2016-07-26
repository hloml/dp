package documents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.jsoup.Jsoup;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import cz.zcu.fav.liks.math.la.DoubleMatrix;
import cz.zcu.fav.liks.math.la.IntVector;
import cz.zcu.fav.liks.ml.classify.Classifier;
import cz.zcu.fav.liks.ml.classify.SupervisedClassifierTrainer;
import cz.zcu.fav.liks.ml.classify.bayes.NaiveBayesGaussianTrainer;
import cz.zcu.fav.liks.ml.classify.maxent.MaxEntTrainer;
import cz.zcu.fav.liks.ml.classify.svm.SVMTrainer;
import cz.zcu.fav.liks.ml.features.FeatureSet;
import cz.zcu.fav.liks.ml.lists.BasicTrainingInstanceList;
import dp.classification.CalculatedMeasure;


/**
 * @author Ladislav Hlom
 * Třída pro práci s dokumenty. Obsahuje metody pro načitání souborů nebo klasifikátorů a jejch ukládání. Dále poskytuje statistiky pro kolekce.
 */
public class MethodsForDocuments {

	
	private HashMap<String, Document> engDocs;
	
	/**
	 * Odstranění html elementů z textu
	 * @param html - text
	 * @return - text bez html elementů
	 */
	public String html2text(String html) {
	    return Jsoup.parse(html).text();
	}
	
	
	/** Uloží dokumenty k přeložení ve formátu, který je vhodný pro překlad externím systémem.
	 * @param filename - výsledný soubor
	 * @param documents - kolekce dokumentů
	 * @throws IOException 
	 */
	private void writeToFileForTranslate(String filename, ArrayList<Document> documents) throws IOException {
		
		PrintWriter pw = new PrintWriter(new FileWriterWithEncoding(filename, "UTF-8"));

		for (Document tmp: documents) {
			pw.println("id: " + tmp.getId());
			pw.println("zpráva: " + tmp.getMessage());
			pw.println("------------------ ");
		}
		pw.close();
	}
	
	
	
	/** Nahraje přeložené dokumenty ze souboru
	 * @param filename - soubor s přeloženými dokumenty
	 * @param documents - kolekce s dokumenty (nepřeloženými)
	 * @param load - Zda kolekce obsahuje nepřeložené dokumenty
	 * @return - vrací kolekci s nahranými přeloženými dokumenty
	 * @throws FileNotFoundException - soubor nenalezen
	 */
	public ArrayList<Document> loadTranslatedDocuments(String filename, ArrayList<Document> documents, boolean load) throws FileNotFoundException {
		String line;
	    InputStream fis;
	    
	    if (!load) {
	    	documents = new ArrayList<>();
	    }
	  
	    HashMap<String, Document> docs = new HashMap<String, Document>();
	    
		try {
			fis = new FileInputStream(filename);		
			InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
			BufferedReader br = new BufferedReader(isr);
			Document doc = new Document();
			String message = "";
			
			boolean full = false;

		    while ((line = br.readLine()) != null) {		        

		    	if (line.startsWith("------------------ ")) {
		    		doc.setMessage(html2text(message));
		    		full = false;
		    		docs.put(doc.getId(), doc);
					doc = new Document();
					message = "";
		    	}  
		    	else if (line.startsWith("id:")) {
		    		String[] s = line.split(": ", 2);
		    		
					doc.setId(s[1].replaceAll("\\s+",""));
					full = true;
		    	}
		    	else if (line.startsWith("zpráva:")) {
		    		String[] s = line.split(": ", 2);
					message += s[1];
		    	}
		    	else {
					if (full) {
						message += line;
					}
		        }	  

		    }

		    br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		for(Entry<String, Document> entry : engDocs.entrySet()) {
		    String key = entry.getKey();
		    Document value = entry.getValue();
		  
		    if (load) {
		    	if (!docs.containsKey(key)) {
		    		documents.remove(value);
		    	}
		    } 
		    else if (docs.containsKey(key)) {
		    	Document val = docs.get(key);
		    	val.setRightCategories(value.getRightCategories());
		    	documents.add(val);
		    }		
		}
	
		return documents;
	}
	
	
	/** Načte dokumenty z XML souboru. Ukládá kolekci dokumentů k přeložení do souboru 
	 * @param filename - jméno XML souboru
	 * @return - vrací kolekci s načtenými dokumenty
	 */
	public ArrayList<Document> loadXMLData(String filename) {
		engDocs = new HashMap<String, Document>();
		ArrayList<Document> documents = new ArrayList<>();	
		ArrayList<Document> documentsEnglish = new ArrayList<Document>();
		
	    try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();	
	    	
			FileInputStream fXmlFile = new FileInputStream(new File(filename));
	
			org.w3c.dom.Document doc = dBuilder.parse(fXmlFile,  "UTF-8");
			doc.getDocumentElement().normalize();
					
			NodeList nList = doc.getElementsByTagName("NewsItem");
				
			documentsEnglish = new ArrayList<Document>();
			ArrayList<Document> documentsDeutsch = new ArrayList<Document>();
			ArrayList<Document> documentsFrench = new ArrayList<Document>();
			
			for (int temp = 0; temp < nList.getLength(); temp++) {
	
				Node nNode = nList.item(temp);
	
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					String code = eElement.getElementsByTagName("Code").item(0).getTextContent();
					Document document = new Document();
					document.setAgenture(code);
					document.setMessage(html2text(eElement.getElementsByTagName("BodyContent").item(0).getTextContent()));
					document.setId(Integer.toString(temp));
					document.addToRightCategory(eElement.getElementsByTagName("Category").item(0).getTextContent());
					
					if (code.equalsIgnoreCase("apa") || code.equalsIgnoreCase("dpa")) {
						documentsDeutsch.add(document);		
					}
					if (code.equalsIgnoreCase("ap")) {
						documents.add(document);
						documentsEnglish.add(document);	
						engDocs.put(document.getId(), document);
					}
					else if (code.equalsIgnoreCase("afp")) {
						documentsFrench.add(document);	
					}					
				}
			}
			
			writeToFileForTranslate("EnglishToTranslate.txt", documentsEnglish);
			
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	    
	    return documents;
	}
	
	

	
	/** Načte všechny dokumenty s koncovkou .txt z adresare. Jméno souboru obsahuje identifikátor kategorie, do kterých patří (při více kategoriích jsou oděleny znakem _)
	 * @param corpusPath - jméno adresáře
	 * @return - vrací kolekci načtených dokumentů
	 * @throws IOException - soubor nenalezen
	 */
	public ArrayList<Document> loadAllDocuments(String corpusPath) throws IOException {
		ArrayList<Document> documents = new ArrayList<>();
		File dir = new File(corpusPath);
		String[] extensions = new String[] { "txt"};		
		List<File> files = (List<File>) FileUtils.listFiles(dir, extensions, true);
		
		// pro vsechny soubory 
		for (File file : files) {			
			FileInputStream fisTargetFile = new FileInputStream(new File(file.getCanonicalPath()));
			String message = IOUtils.toString(fisTargetFile, "UTF-8");
			String allCategories = FilenameUtils.removeExtension(file.getName());
			Document doc = new Document();
			doc.setMessage(html2text(message));
    		
    		String[] a = allCategories.split("_", 2);
    		for (String category : a[1].split("_")) {
				doc.addToRightCategory(category);
    		}
    		
    		documents.add(doc);
		}	
		
		return documents;
	}
	
	
	
	/** Načte všechny dokumenty z adresáře. Nalezené dokumenty jsou zapsány do souboru k přeložení.
	 * @param name - jméno vygenerovaného souboru s načtenými dokumenty
	 * @param corpusPath - jméno adresáře
	 * @return - vrací kolekci načtených dokumentů
	 * @throws IOException - soubor nenalezen
	 */
	public ArrayList<Document> loadDocumentsFromFile(String name, String corpusPath) throws IOException {
		ArrayList<Document> documents = getFilesInDirectories(corpusPath);	
		writeToFileForTranslate(name, documents);
		return documents;
	}
	
	
	/**
	 * Načte všechny dokumenty z adresáře. Musí být dodrženy názvy souborů ("training","test","unknown","bbc", "bbcTranslated") viz přiložené soubory.
	 * @param corpusPath - jméno souboru
	 * @return - vrací kolekci načtených dokumentů
	 * @throws FileNotFoundException - soubor nenalezen
	 * @throws IOException - soubor nenalezen
	 */
	public ArrayList<Document> getFilesInDirectories(String corpusPath) throws FileNotFoundException, IOException {
		ArrayList<Document> documents = new ArrayList<>();
		engDocs = new HashMap<String, Document>();
		
		Set<String> ignoredFiles = new HashSet<String>(Arrays.asList(
			     new String[] {"training","test","unknown","bbc", "bbcTranslated"}
			));
		
		
		List<File> files = (List<File>) FileUtils.listFilesAndDirs(new File(corpusPath), new NotFileFilter(TrueFileFilter.INSTANCE), DirectoryFileFilter.DIRECTORY);

		int id = 0;
		
		// pro vsechny soubory 
		for (File file : files) {				
			List<File> filesInDir = (List<File>) FileUtils.listFiles(file, null, true);
			if (!ignoredFiles.contains(file.getName())) {
				for (File fileDoc : filesInDir) {	
					FileInputStream fisTargetFile = new FileInputStream(new File(fileDoc.getCanonicalPath()));
					String message = IOUtils.toString(fisTargetFile, "UTF-8");
					
					String category = FilenameUtils.removeExtension(file.getName());
					Document doc = new Document();
					doc.setAgenture("ap");        // pro klasifikaci nastavime stejne priznaky jako pro kolekci od ctk
					doc.setMessage(html2text(message));	    		
					doc.addToRightCategory(category);
					doc.setId(String.valueOf(id++));
					engDocs.put(doc.getId(), doc);
		    		documents.add(doc);
				}
			}
		}	
			
		return documents;
	}
	
	
	

	
	/** Spočítá přesnost, úplnost, f-míru a zapíše výsledky do souboru
	 * @param filename - jméno souboru
	 * @param documentsForTesting - kolekce otestovaných dokumentů
	 * @return - strukturu s výsledky
	 */
	public static CalculatedMeasure writeResults(String filename, ArrayList<Document> documentsForTesting) {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(filename, "UTF-8");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
   
		int TP = 0;		// True positive
		int FP = 0;		// False positive
		int FN = 0;		// False negative
		int TPDoc = 0;	// True positive pro jeden dokument (k vypocteni FN)
		
		// Spocti hodnoty pro vsechny dokumenty k testovani a zapis vysledky
		for(int ci = 0; ci < documentsForTesting.size() ; ci++) {
			writer.println(ci + " - Spravne kategorie dokumentu : " + documentsForTesting.get(ci).getRightCategories());
			writer.println(ci + " - Natipovane kategorie dokumentu : " + documentsForTesting.get(ci).getCategories());
			TPDoc = 0;
			
			// projdi natipovane kategorie dokumentu
			for(String tmp: documentsForTesting.get(ci).getCategories()) {
				// spravne tipnuty dokument
				if (documentsForTesting.get(ci).getRightCategories().contains(tmp)) {	
					TP++;
					TPDoc++;
				}
				else {
					FP++;
				}
			}
			
			if (TPDoc < documentsForTesting.get(ci).getRightCategories().size()) {
				FN += documentsForTesting.get(ci).getRightCategories().size() - TPDoc;
			}		
		}
		double precision = (double) TP/ (TP + FP);
		double recall  = (double) TP/ (TP + FN);
		double fMeasure = (double) 2 * ((precision * recall) / (precision + recall));
		writer.println("precision: " + precision );
		writer.println("recall: " + recall );			
		writer.println("f measure: " + fMeasure);	
		writer.flush();
		writer.close();
		
		CalculatedMeasure calculatedMeasure = new CalculatedMeasure(precision, recall, fMeasure);
		return calculatedMeasure;
	}
	
	
	/** Uloží natrénovaný klasifikátor
	 * @param filename - jméno uloženého klasifikátoru
	 * @param classifier - klasifikátor k uložení
	 */
	public void saveClassifier(String filename, Classifier classifier) {
		
		File f = new File(filename);
		if (!f.exists()) {
			try {
				FileOutputStream fileOut =
				new FileOutputStream(filename);
				ObjectOutputStream out = new ObjectOutputStream(fileOut);
				out.writeObject(classifier);
				out.close();
				fileOut.close();
				System.out.println("Serialized classificator saved in " + filename);
			} 
			catch(IOException i){
		      i.printStackTrace();
			}
		} 
		
	}
	
	
	/**
	 * Načte uložený klasifikátor
	 * @param filename - jméno klasifikátoru
	 * @return - vrací načtený klasifikátor
	 */
	public Classifier loadClassifier(String filename) {
		File f = new File(filename);
		Classifier classifier = null;
		// pokud existuje serializovany klasifikator - pak je nacten
		if (f.exists() && f.canRead()) {
			System.out.println("Klasifikator nacten");
		   try
		      {
		         FileInputStream fileIn = new FileInputStream(filename);
		         ObjectInputStream in = new ObjectInputStream(fileIn);
		         classifier = (Classifier) in.readObject();
		         in.close();
		         fileIn.close();
		      }catch(IOException i)
		      {
		         i.printStackTrace();
		         return null;
		      }catch(ClassNotFoundException c)
		      {
		         System.out.println(filename + " class not found");
		         c.printStackTrace();
		         return null;
		      }		
		}
		return classifier;
	}
	
	/** Vytvoří klasifikátor zvoleného typu (k dispozici maximální entropie, SVM a naive bayes) a natrénuje ho,
	 * @param numLabels - počet označených dokumentů
	 * @param trainingSet - trénovací kolekce
	 * @param set - kolekce příznaků
	 * @param classificatorType - typ klasifikátoru (maximální entropie, SVM a naive bayes)
	 * @return - vrací klasifikátor zvoleného typu
	 */
	public <T> Classifier createClassifier(int numLabels, BasicTrainingInstanceList<T> trainingSet, FeatureSet<T> set, String classificatorType) {
		DoubleMatrix trainingData = set.getData(trainingSet);
		IntVector trainingLabels = set.getLabels(trainingSet);
		SupervisedClassifierTrainer trainer = null;
		
		switch (classificatorType) {
		case "SVM":
			trainer = new SVMTrainer();		
			break;
		case "ME":
			trainer = new MaxEntTrainer();	
			break;
		case "bayes":
			trainer = new NaiveBayesGaussianTrainer();	
			break;
		}
		
		// train classifier
		Classifier classifier = trainer.train(trainingData, trainingLabels, numLabels);
	
		return classifier;
	}
	
	
	
	/** Spočítá počet slov v kolekci 
	 * @param docs - kolekce dokumentů
	 * @return - počet slov
	 */
	public int countWords(ArrayList<Document> docs){
		int count = 0;
		for (Document doc: docs) {
		        String[] words = doc.getMessage().split(" ");
		        count += words.length;
	        }
		
	    return count;
	}
	
	

	/** Zjistí distribuci dokumentů pro počet kategorií na dokument
	 * @param docs - kolekce dokumentů
	 * @return - mapa (klíč - počet kategorií, hodnota - počet dokumentů)
	 */
	public HashMap<Integer, Integer> documentLabelDistribution(ArrayList<Document> docs) {
		HashMap<Integer, Integer> distribution = new HashMap<Integer, Integer>();
		for (Document doc: docs) {
			Integer size = distribution.get(doc.getRightCategories().size());
			if (size == null) {
				distribution.put(doc.getRightCategories().size(), 1);
			}
			else {
				distribution.put(doc.getRightCategories().size(), size + 1);
			}
			
		}
		
		return distribution; 
	}
	
	
	/** Zobrazí distribuci počtu slov pro dokumenty, intervaly po 50 slovech
	 * @param docs - kolekce dokumentů
	 * @return - mapa (klíč - počet slov, hodnota - počet dokumentů)
	 */
	public HashMap<Integer, Integer> documentWordDistribution(ArrayList<Document> docs) {
		HashMap<Integer, Integer> distribution = new HashMap<Integer, Integer>();
		
		for (Document doc: docs) {
	
	        String[] words = doc.getMessage().split(" ");
	        //noOWoInString.addAll(words);
	        int count = roundToNearest(words.length);
			
	        Integer size = distribution.get(count);
	        
			if (size == null) {
				distribution.put(count, 1);
			}
			else {
				distribution.put(count, size + 1);
			}
			
		}
		
		return distribution; 
	}
	
	
	/** Zaokrouhlí číslo na nejbližší pro hodnoty 50 
	 * @param x - číslo k zaokrouhlení
	 * @return - zaokrouhlené číslo
	 */
	private int roundToNearest(int x) {
	    if (x%50 < 25) {
	        return x - (x%50); 
	    }
	    else if (x%50 > 25) {
	        return x + (50 - (x%50)); 
	    }
	    else if (x%50 == 25) {
	        return x + 25; 
	    }     
	    return x;
	}
	
	
	
	/** Do souboru statistics.txt vypíše statistiky pro kolekci. Jedná se o distribuci kategorií a slov. Dále počty dokumentů pro jednotlivé kategorie.
	 * @param documents - kolekce dokumentu
	 * @throws IOException - soubor nenalezen
	 */
	public void statisticsForDocument(ArrayList<Document> documents) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriterWithEncoding("statistics.txt", "UTF-8"));
	
		int words = countWords(documents);
		pw.println("Pocet slov v kolekci: " + words);

		pw.println("Distribuce poctu kategorii pro kolekci:");
		HashMap<Integer, Integer> distribution = documentLabelDistribution(documents);
		int tmpCount = 0;
		for(Entry<Integer, Integer> entry : distribution.entrySet()) {
			Integer key = entry.getKey();
		    Integer value = entry.getValue();
		    pw.println(key + ": " + value);
		    tmpCount += value;
		}
		
		pw.println("Celkovy pocet dokumentu: " + tmpCount);
		pw.println("Distribuce rozlozeni poctu slov pro kolekci: " + tmpCount);

		distribution = documentWordDistribution(documents);
		tmpCount = 0;
		
		TreeMap<Integer, Integer> sorted = new TreeMap<Integer, Integer>(distribution);
		
		
		for(Entry<Integer, Integer> entry : sorted.entrySet()) {
			Integer key = entry.getKey();
		    Integer value = entry.getValue();
		    pw.println(key + ": " + value);
		    tmpCount += value;
		}
		
		pw.println("Celkovy pocet dokumentu: " + tmpCount);
		
		Set<String> cat = new HashSet<String>();
		HashMap<String, Integer> distributionCat = new HashMap<String, Integer>();
		
		for (Document doc:documents) {
			for (String category: doc.getRightCategories()) {
				
				Integer size = distributionCat.get(category);
		        
				if (size == null) {
					distributionCat.put(category, 1);
				}
				else {
					distributionCat.put(category, size + 1);
				}
				
				cat.add(category);
			}
		}
		
		TreeMap<String, Integer> sortedCat = new TreeMap<String, Integer>(distributionCat);
		tmpCount = 0;
		for(Entry<String, Integer> entry : sortedCat.entrySet()) {
			String key = entry.getKey();
		    Integer value = entry.getValue();
		    pw.println(key + ": " + value);
		    tmpCount += value;
		}
		
		pw.println("Celkovy pocet dokumentu: " + tmpCount);
		pw.println("Celkovy pocet kategorii " + cat.size());
		pw.close();
	}	
}
