

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;






import org.junit.BeforeClass;
import org.junit.Test;

public class TestLDAmethods {

	static ParallelLDA parallelLDA = null;	
	static LDAmethods ldaMethods = null;
	static List<String> testDocumentEN = Arrays.asList("Test", "document");
	static List<String> testDocumentCZ = Arrays.asList("Testovací", "dokument");
	
   @BeforeClass
    public static void init() {
    	System.out.println("Starting tests - Loading LDA");
    	
		Properties prop = new Properties();
		InputStream input = null;

		try {
			input = new FileInputStream("config.properties");
			// load a properties file
			prop.load(input);
			parallelLDA = ParallelLDA.load(prop.getProperty("ldaFile"));
			ldaMethods = new LDAmethods();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	
	
	@Test
	public void testPrepareEnglishDoc() {
		System.out.println("Testing Prepare for english document");
			
		String[] docEN = ldaMethods.prepareDocument(testDocumentEN, "ap");
		assertEquals( "EN_Test",docEN[0]);
		assertEquals( "EN_document",docEN[1]);	
	}


	@Test
	public void testPrepareCzechDoc() {
		System.out.println("Testing Prepare for czech document");
			
		String[] docCZ = ldaMethods.prepareDocument(testDocumentCZ, "");
		assertEquals( "CZ_Testovací",docCZ[0]);
		assertEquals( "CZ_dokument",docCZ[1]);	
	}
	
	
	@Test
	public void testGetVectorEnglishDoc() {
		System.out.println("Testing get vector for english document");
			
		String[] docEN = ldaMethods.prepareDocument(testDocumentEN, "ap");
		List<Double> vector =  ldaMethods.getVector(parallelLDA, docEN);
		assertEquals( vector.size(), 100);
	}


	@Test
	public void testGetVectorCzechDoc() {
		System.out.println("Testing get vector for czech document");
			
		String[] docCZ = ldaMethods.prepareDocument(testDocumentCZ, "");
		List<Double> vector =  ldaMethods.getVector(parallelLDA, docCZ);
		assertEquals( vector.size(), 100);
	}
	
	
	private boolean vectorEquals(List<Double> vector1, List<Double> vector2) {		
        for (int i=0; i < vector1.size(); i++) {
        	if (vector1.get(i).compareTo(vector2.get(i)) != 0) {

        		return false;
        	}
        }
        return true;
    }
	
	
	@Test
	public void testGetVectorReturnDifferentVectors() {
		System.out.println("Testing get vector returns different vectors");
			
		String[] docCZ = ldaMethods.prepareDocument(testDocumentCZ, "");
		List<Double> vector =  ldaMethods.getVector(parallelLDA, docCZ);
		
		String[] docEN = ldaMethods.prepareDocument(testDocumentEN, "ap");
		List<Double> vector2 =  ldaMethods.getVector(parallelLDA, docEN);
			
		assertEquals(false, vectorEquals(vector, vector2));
	}
	
	
}
