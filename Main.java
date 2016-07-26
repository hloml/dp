import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import org.apache.log4j.PropertyConfigurator;


/** Hlavní třída
 * @author Ladislav Hlom
 *
 */
public class Main {
		
	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
		Tests data = new Tests();	
		Properties props = new Properties();
		props.load(new FileInputStream(new File("log4j.properties")));
		PropertyConfigurator.configure(props);
		data.run();
			
	}	
}
