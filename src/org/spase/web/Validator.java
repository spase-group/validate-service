/**
 * Validates a SPASE description using the appropriate version of the data model.
 *
 * @author Todd King
 * @version 1.00 2006
 */

package org.spase.web;

import igpp.xml.Pair;
import igpp.xml.XMLGrep;

import igpp.web.SimpleFTP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.net.URL;
import java.net.HttpURLConnection;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Hashtable;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Schema;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import javax.servlet.jsp.JspWriter;
import javax.servlet.http.HttpServletRequest;

// import javazoom.upload.*
import javazoom.upload.UploadBean;
import javazoom.upload.MultipartFormDataRequest;
import javazoom.upload.UploadFile;

import com.sun.org.apache.xerces.internal.impl.io.MalformedByteSequenceException;

public class Validator extends DefaultHandler {
	private String mVersion = "1.0.4";

	private boolean mVerbose = false;

	private String mUrl = null;
	private String mFile = null;
	private String mXsdVersionDefault = "2.2.3";	// Most recent version
	private String mXsdVersion = "-";	// Declared in file
	// private String mRegistry = "http://www.spase-group.org/registry/resolver";
	private String mRegistry = "https://hpde.io/";

	private boolean mCheckURL = true;
	private boolean mCheckID = true;

	MultipartFormDataRequest mMultiRequest = null;

	private ArrayList<Integer> mMarkLine = new ArrayList<Integer>();
	private ArrayList<String> mBadRef = new ArrayList<String>();
	private Schema mSchema = null;

	private JspWriter mWriter = null;
	private PrintStream mStream = null;

	private String mLastError = "";
	private String mLastWarning = "";

	private String mXsdUrl = "https://spase-group.org/data/schema/spase-2.2.3.xsd";

	/**
	 * Validate a SPASE resource description using a specified version of the
	 * data dictionary.
	 * <p>
	 * Usage:<blockquote> Validate url version </blockquote>
	 */
	public static void main(String args[]) {
		Validator me = new Validator();

		if (args.length < 1) {
			me.showHelp();
			System.exit(1);
		}

		me.setWriter(System.out);

		try {
			for (int i = 0; i < args.length; i++) {
				if (args[i].compareTo("-v") == 0) { // Verbose
					me.mVerbose = true;
				} else if (args[i].compareTo("-h") == 0) { // Help
					me.showHelp();
				} else if (args[i].compareTo("-s") == 0) { // Load schema in
															// file
					i++;
					if (i < args.length) {
						me.loadSchema(args[i]);
					}
				} else if (args[i].compareTo("-n") == 0) { // Set schema from
															// version number
					i++;
					if (i < args.length) {
						me.mXsdVersion = args[i];
						if( ! me.mXsdVersion.equals("-")) me.setSchemaFromVersion(me.mXsdVersion);
					}
				} else if (args[i].compareTo("-l") == 0) { // URL to lookup of
															// registry
					i++;
					me.mRegistry = args[i];
				} else if (args[i].compareTo("-i") == 0) { // Check ID
					me.mCheckID = true;
				} else if (args[i].compareTo("-u") == 0) { // Check URL
					me.mCheckURL = true;
				} else { // Load resource at path
					me.mUrl = args[i];
					me.doAction();
				}
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
	}

	/**
	 * Display help information.
	 **/
	public void showHelp() {
		System.out.println("Version: " + mVersion);
		System.out.println("SPASE Resource Description grammar checker.");
		System.out.println("");
		System.out
				.println("Checks a resource description for compliance to a specified version");
		System.out.println("of the SPASE data model.");
		System.out.println("");
		System.out.println("Usage: " + getClass().getName()
				+ "{options} file|url");
		System.out.println("");
		System.out.println("Options:");
		System.out.println("");
		System.out.println("   -h : This help information.");
		System.out.println("   -v : Set verbose mode.");
		System.out
				.println("   -n : Version number of the model the use for check.");
		System.out
				.println("   -s : File containing an XML Schema specification to use for check.");
		System.out.println("   -l : URL to the Registry lookup service.");
		System.out.println("   -i : Turn on checking of resource IDs.");
		System.out.println("   -u : Turn on checking of referenced URLs.");
		System.out.println("");
	}

	/**
	 * Determines if enough information has been specified to process the
	 * request.
	 **/
	public boolean isReady() {
		if (mUrl == null && mFile == null)
			return false;

		if (mUrl != null && mUrl.length() > 0)
			return true;
		if (mFile != null && mFile.length() > 0)
			return true;

		return false;
	}

	/**
	 * Perform the action
	 **/
	public void loadOptions(HttpServletRequest request) throws Exception {
		mMultiRequest = null;

		// Take parameters from request
		if (request != null) {
			setUrl(request.getParameter("url"));
			setVersion(request.getParameter("version"));
			setCheckID(request.getParameter("checkid"));
			setCheckURL(request.getParameter("checkurl"));
			setFile(null);
		}

		// If a multipart request we must process the parameters differently
		if (request != null
				&& MultipartFormDataRequest.isMultipartFormData(request)) {
			mMultiRequest = new MultipartFormDataRequest(request);
			if (mMultiRequest != null) {
				setUrl(mMultiRequest.getParameter("url"));
				setVersion(mMultiRequest.getParameter("version"));
				setCheckID(mMultiRequest.getParameter("checkid"));
				setCheckURL(mMultiRequest.getParameter("checkurl"));
				setFile(null);
				// Set filename
				Hashtable files = mMultiRequest.getFiles();
				if ((files != null) && (!files.isEmpty())) {
					for (Enumeration e = files.keys(); e.hasMoreElements();) {
						String name = (String) e.nextElement();
						UploadFile file = (UploadFile) files.get(name);
						if (file != null) {
							InputStream inputStream = file.getInpuStream(); // Typo
																			// is
																			// in
																			// UploadFile
							if (inputStream != null)
								setFile(name);
						}
					}
				}
				if (mFile != null && mFile.length() > 0)
					setUrl(null); // Only one allowed
			}
		}
		if (mXsdVersion == null)
			mXsdVersion = "-"; // Default - Use in file
	}

	/**
	 * Perform the action
	 **/
	public void doAction() throws Exception {
		if(mXsdVersion.equals("-")) {
			mXsdVersion = getVersionFromFile();
		}
		if (mSchema == null)
			setSchemaFromVersion(mXsdVersion);
		validate();
	}

	/**
	 * Load a schema for validating an XML document.
	 **/
	public void loadSchema(String xsdUrl) {
		try {
			String language = XMLConstants.W3C_XML_SCHEMA_NS_URI;
			SchemaFactory schemaFactory = SchemaFactory.newInstance(language);
			StreamSource ss = new StreamSource(xsdUrl);
			mSchema = schemaFactory.newSchema(ss);
		} catch (Exception e) {
			println("Using URL: " + xsdUrl);
			println("Error: " + e.getMessage());
		}
	}

	/**
	 * Open the file passed to the service. A file may be passed as a URL
	 * reference or as an upload.
	 **/
	public String getNamespace() throws Exception {
		String namespace = "";
		
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		factory.setXIncludeAware(true);
		
	    DocumentBuilder db;
	    Document doc = null;
	    try {
	        db = factory.newDocumentBuilder();
	        doc = db.parse(openFile(false));
	    } catch (ParserConfigurationException e) {
	        e.printStackTrace();
	    } catch (SAXException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    
	    // We look for a Tag "Version" because this works whether or not a namespace is used.
	    // In a SPASE document there should be only one instance of a "Version"
    	NodeList nodeList = doc.getElementsByTagName("Spase");
    	// Get text in tag
    	for(int x=0,size= nodeList.getLength(); x<size; x++) {
	        Node node = nodeList.item(x);
	        namespace = node.getNamespaceURI();
	        println("Namespace before: " + namespace);
	        if(namespace == null) doc.renameNode(node, "http://www.spase-group.org/data/schema", node.getNodeName());
	        namespace = node.getNamespaceURI();
	        println("Namespace after: " + namespace);
    	}
            
        println("Namespace: " + namespace);
        return namespace;
	}

	/**
	 * Open the file passed to the service. A file may be passed as a URL
	 * reference or as an upload.
	 **/
	public String getVersionFromFile() throws Exception {
		String version = "";
		
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		
	    DocumentBuilder db;
	    Document dom = null;
	    try {
	        db = factory.newDocumentBuilder();
	        dom = db.parse(openFile(false));
	    } catch (Exception e) {
	        // e.printStackTrace();
	    	return mXsdVersion;	// No change
	    }

	    // We look for a Tag "Version" because this works whether or not a namespace is used.
	    // In a SPASE document there should be only one instance of a "Version"
    	NodeList nodeList = dom.getElementsByTagName("Version");
    	// Get text in tag
    	for(int x=0,size= nodeList.getLength(); x<size; x++) {
            NodeList subList = nodeList.item(x).getChildNodes();

            if (subList != null && subList.getLength() > 0) {
                version += subList.item(0).getNodeValue();
            }
    	} 	  
    	version = version.trim();
	    return version;
	}
	
	public InputStream openWellFormedFile(Boolean withMessage) throws Exception {
		// Open document and check if well formed.
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		factory.setXIncludeAware(true);
		
	    DocumentBuilder db;
	    Document doc = null;
	    try {
	        db = factory.newDocumentBuilder();
	        InputStream input = openFile(false);
	        doc = db.parse(input);
	        input.close();
	    } catch (Exception e) {
	    	// be silent - error handling elsewhere
	        // e.printStackTrace();
	    	return openFile(withMessage);
	    }
	    
	    // We look for a Tag "Version" because this works whether or not a namespace is used.
	    // In a SPASE document there should be only one instance of a "Version"
	    Boolean fixed = false;
	    
    	NodeList nodeList = doc.getElementsByTagName("*");
    	// Get text in tag
    	for(int x=0,size= nodeList.getLength(); x<size; x++) {
	        Node node = nodeList.item(x);
	        String namespace = node.getNamespaceURI();
	        if(namespace == null) {	// No namespace - set to spase
	        	doc.renameNode(node, "http://www.spase-group.org/data/schema", node.getNodeName());
	        	fixed = true;
	        }
    	}

    	if(fixed) { // write the content into a byte array and return stream
    		TransformerFactory transformerFactory = TransformerFactory.newInstance();
    		Transformer transformer = transformerFactory.newTransformer();
    		DOMSource source = new DOMSource(doc);
    		ByteArrayOutputStream outByte = new ByteArrayOutputStream();
    		
    		// StreamResult fileResult = new StreamResult(new File("C:\\temp\\file.xml"));
    		// transformer.transform(source, fileResult);
    		
    		StreamResult result = new StreamResult(outByte);
    		transformer.transform(source, result);
    		ByteArrayInputStream inputByte = new ByteArrayInputStream(outByte.toByteArray()); 
    		
    		return inputByte;
        }
    	
    	return openFile(withMessage);	// Return default stream
	}
	/**
	 * Open the file passed to the service. A file may be passed as a URL
	 * reference or as an upload.
	 **/
	public InputStream openFile(Boolean withMessage) throws Exception {
		InputStream inputStream = null;

		if (mUrl != null) { // From URL
			try {
				URL file = new URL(mUrl);
				inputStream = file.openStream();
			} catch (Exception e) {
				// Try as a file
				inputStream = new FileInputStream(mUrl);
			}
			if (withMessage)
				println(" " + mUrl);
		} else { // Must be an upload
			if (mMultiRequest != null) { // From upload
				String name;
				UploadFile file;

				UploadBean upload = new UploadBean();
				upload.setFilesizelimit(10240); // 10Kbytes

				Hashtable files = mMultiRequest.getFiles();
				if ((files != null) && (!files.isEmpty())) {
					for (Enumeration e = files.keys(); e.hasMoreElements();) {
						name = (String) e.nextElement();
						file = (UploadFile) files.get(name);
						if (file == null)
							return null; // Error
						inputStream = file.getInpuStream(); // Typo is in
															// UploadFile
						setFile(name); // Save name
						break; // First one only
					}
				}
				if (withMessage)
					println(" **upload** ");
			} else {
				if (withMessage)
					println(" Not input specified. ");
				return null;
			}

		}

		return inputStream;
	}

	/**
	 * Perform a validation on an XML document using the the currently loaded
	 * schema. Errors are caught and output with the line number where the error
	 * occurred. After processing the XML document is output in an table format
	 * with each line numbered and those lines with errors highlighted.
	 */
	public void validate() throws Exception {
		InputStream inputStream = null;

		String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
		String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema"; 
		String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";
		
		/*
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(true);
		factory.setXIncludeAware(true);
		try {
		   // factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
		   // factory.setAttribute(JAXP_SCHEMA_SOURCE,  new File("/temp/spase-2_2_2.xsd")); 
  		   // factory.setAttribute( "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation", "/temp/spase-2_2_2.xsd");
  		   factory.setSchema(mSchema);
		   // factory.setAttribute(JAXP_SCHEMA_SOURCE, new InputSource(new URL(mXsdUrl).openStream()));
	       DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(this);
			inputStream = openFile(true);
			builder.parse(inputStream);
			inputStream.close();
		} 
		catch (IllegalArgumentException err) {
			  // Happens if the parser does not support JAXP 1.2
			 println("Something happened: " + err.getMessage());
		}
		*/
		
		// Use a validating parser with namespaces
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(true);
		factory.setNamespaceAware(true);
		factory.setXIncludeAware(true);
		
		// factory.setSchema(mSchema);
		
		if (mVerbose)
			println("Using schema: " + mXsdUrl);
		
		// Validate the file
		// open document
		if (mVerbose)
			print("Validating: ");
		inputStream = openWellFormedFile(true);

		if (withHTML())
			println("<p>");
		if (inputStream == null) {
			println("No input stream.");
			return;
		}

		try {
			if(mXsdVersion.equals("-")) {
				setSchemaFromVersion(mXsdVersionDefault);
			}
			// Parse the input
			SAXParser saxParser = factory.newSAXParser();
			saxParser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
			saxParser.setProperty(JAXP_SCHEMA_SOURCE, new InputSource(new URL(mXsdUrl).openStream()));
			// saxParser.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation", "/temp/spase-2_2_2.xsd");
			// saxParser.setProperty("http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation", "/temp/spase-2_2_2.xsd");
			saxParser.parse(inputStream, this);	
		} catch (SAXParseException err) {
			// Error generated by the parser
			println("Line " + err.getLineNumber() + " " + err.getMessage());
			println();
			mMarkLine.add(err.getLineNumber());
		} catch (MalformedByteSequenceException err) {
			// Error generated by the parser
			println("Error: "
					+ err.getMessage()
					+ " Which may be caused by an improper character encoding of the document.");
			mMarkLine.add(0);
		} catch (SAXException err) {
			// Error generated by this application
			// (or a parser-initialization error)
			println("Error: " + err.getMessage());
			println();
			mMarkLine.add(0);
		} catch (ParserConfigurationException pce) {
			// Parser with specified options can't be built
			pce.printStackTrace();
			mMarkLine.add(0);
		} catch (IOException err) {
			// I/O error
			println("Error: " + err.getMessage());
			println();
			mMarkLine.add(0);
		} catch (Exception err) {
			println("Error: " + err.getMessage());
			println();
			mMarkLine.add(0);
		}
		
		inputStream.close();
    	
		if (mCheckURL || mCheckID) {
			// re-open document for referential checking
			inputStream = openFile(false);
			try {
				checkReferences(inputStream);
			} catch (Exception e) {
				// silent - not well formed. Reported else where.
			}
			inputStream.close();

			if (!mBadRef.isEmpty()) {
				println("Invalid references:");
				println();
				// Determine line numbers
				int n = 0;
				String buffer = "";
				String message = "";

				inputStream = openFile(false);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(inputStream));
				while ((buffer = reader.readLine()) != null) {
					n++;
					for (String item : mBadRef) {
						if (buffer.indexOf(item) != -1) {
							if (withHTML()) {
								message = "<a href=#" + n + ">Line " + n
										+ "</a>: " + item;
							} else {
								message = "Line " + n + ": " + item;
							}
							println(message);
							println();
						}
					}
				}
				println("<br/>");
				inputStream.close();
			}
		}

		// re-open document for printing
		inputStream = openFile(false);

		// Show errors or success
		try {
			String buffer;
			int n = 0;
			int markAt = -1;
			String color;
			String marker;

			if (mMarkLine.isEmpty() && mBadRef.isEmpty()) {
				if (withHTML())
					println("<b>");
				println("Congratulations - its valid!");
				if (withHTML())
					println("</b><br>");
				if (!withHTML())
					return;
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					inputStream));
			Iterator<Integer> it = mMarkLine.iterator();
			if (it.hasNext()) {
				markAt = ((Integer) it.next()).intValue();
			}

			if (withHTML())
				println("<table>");
			while ((buffer = reader.readLine()) != null) {
				n++;
				color = "#DDDDDD";
				marker = "  ";
				// Check if parser or schema error
				if (markAt == n) {
					color = "#EEEE00";
					marker = "=>";
					while (it.hasNext()) {
						markAt = ((Integer) it.next()).intValue();
						if (markAt != n)
							break; // Sometimes multiple errors on one line
					}
				}
				// Check for reference error
				for (String item : mBadRef) {
					if (buffer.indexOf(item) != -1) {
						color = "#EEEE00";
						marker = "=>";
						break;
					}
				}
				if (withHTML()) {
					println("<tr bgcolor=" + color + "><td><a id=" + n + ">"
							+ n + "</a>" + "</td><td>" + fixHTML(buffer)
							+ "</td>");
				} else {
					println(marker + n + "  " + buffer);
				}
			}
			if (withHTML())
				println("</table>");
			reader.close();
		} catch (Exception e) {
			println("Error: " + e.getMessage());
		}
	}

	/**
	 * Check references (URL and ID) in the document
	 **/
	public void checkReferences(InputStream inputStream) throws Exception {
		Document doc = XMLGrep.parse(inputStream);
		ArrayList<Pair> docIndex = XMLGrep.makeIndex(doc, "");

		if (mCheckID) {
			ArrayList<String> selfList = igpp.util.Text.uniqueList(
					XMLGrep.getValues(docIndex, ".*/ResourceID"), true);
			ArrayList<String> idList = igpp.util.Text.uniqueList(
					XMLGrep.getValues(docIndex, ".*/.*ID"), true);
			ArrayList<String> priorIDList = igpp.util.Text.uniqueList(
					XMLGrep.getValues(docIndex, ".*/PriorID"), true);
			if (idList != null) {
				for (String id : idList) {
					if (igpp.util.Text.isInList(id, priorIDList))
						continue; // skip
					if (igpp.util.Text.isInList(id, selfList))
						continue; // Don't check self
					if (!lookupID(id)) {
						mBadRef.add(id);
					}
				}
			}
		}

		// Check URLs
		if (mCheckURL) {
			ArrayList<String> urlList = igpp.util.Text.uniqueList(
					XMLGrep.getValues(docIndex, ".*/URL"), true);
			if (urlList != null) {
				for (String url : urlList) {
					if (!checkURL(url)) {
						mBadRef.add(url);
					}
				}
			}
		}
	}

	/**
	 * Lookup an identifier
	 * 
	 * @param id
	 *            the resource ID string to lookup at the Registry service.
	 * 
	 * @return <code>true</code> if valid, <code>false</code> otherwise.
	 * 
	 * @author Todd King
	 * @author UCLA/IGPP
	 * @since 1.0
	 **/
	public boolean lookupID(String id) {
		boolean valid = false;

		// Look for file at https://hpde.io
		
		String path = id.replace("spase://", "");	// Remove "spase://" prefix
		return checkURL(mRegistry + path + ".xml");
		
		// Old method using registry service
		// Query server
		// try {
		//	Document doc = XMLGrep.parse(mRegistry + "?c=yes&i=" + id);
		//	ArrayList<Pair> docIndex = XMLGrep.makeIndex(doc, "");
		//	if (XMLGrep.getFirstValue(docIndex, ".*/Known", null) != null)
		//		valid = true;
		//} catch (Exception e) {
		//	valid = false;
		//	if (mVerbose)
		//		System.out.println(e.getMessage());
		//}

		// return valid;
	}

	/**
	 * Check a URL by attempting to establish a connection and retrieve the
	 * header information.
	 * 
	 * @param urlSpec
	 *            the URL to check.
	 * 
	 * @return <code>true</code> if valid, <code>false</code> otherwise.
	 * 
	 * @author Todd King
	 * @author UCLA/IGPP
	 * @since 1.0
	 **/
	public boolean checkURL(String urlSpec) {
		boolean valid = false;
		try {
			URL url = new URL(urlSpec);

			if (url.getProtocol().compareToIgnoreCase("ftp") == 0) {
				valid = checkFTP(urlSpec);
			} else { // Try as URL
				HttpURLConnection con = (HttpURLConnection) url
						.openConnection();

				con.setRequestMethod("HEAD");
				con.connect();
				con.disconnect();

				valid = true;
			}
		} catch (Exception e) {
			valid = false;
			if (mVerbose)
				System.out.println(e.getMessage());
		}

		return valid;
	}

	/**
	 * Check an FTP protocol URL by attempting to establish an annonymous
	 * connection.
	 * 
	 * @param urlSpec
	 *            the URL of an FTP request.
	 * 
	 * @return <code>true</code> if valid, <code>false</code> otherwise.
	 * 
	 * @author Todd King
	 * @author UCLA/IGPP
	 * @since 1.0
	 **/
	public boolean checkFTP(String urlSpec) {
		SimpleFTP ftp = new SimpleFTP();
		boolean status = true;

		try {
			String username = null;
			String password = null;
			URL url = new URL(urlSpec);

			// Get port information - if specified
			// int port = url.getPort();
			// if(port != -1) ftp.setPort(port);

			// Get user information - if specified
			String userInfo = url.getUserInfo();
			if (userInfo != null) {
				String part[] = userInfo.split(":", 2);
				username = part[0];
				if (part.length > 1)
					password = part[1];
			}

			// Get pathname to remote file
			String pathName = url.getFile();
			if (pathName.startsWith("/"))
				pathName = pathName.substring(1); // Drop leading slash

			// Ready to go
			ftp.setVerboseMode(false);
			ftp.connect(url.getHost(), username, password);
			ftp.setVerboseMode(false);

			// ftp.getFile(pathName, out);

		} catch (Exception e) {
			System.out.println("Error while adding: " + urlSpec + "\nReason: "
					+ e.getMessage() + "\n");
			status = false;
		} finally {
			if (ftp.isConnected()) {
				ftp.disconnect();
			}
		}

		return status;
	}

	// ===========================================================
	// SAX DocumentHandler methods
	// ===========================================================

	/**
	 * Called by the XML parser when the URL for document to validate is set.
	 */
	public void setDocumentLocator(Locator l) {
		// Save this to resolve relative URIs or to give diagnostics.
		// if(withHTML()) println("<b>");
		// println(l.getSystemId());
		// if(withHTML()) println("</b><p>");
	}

	/**
	 * Called by the XML parser at the start or processing.
	 */
	public void startDocument() throws SAXException {
		mMarkLine.clear();
	}

	/**
	 * Called by the XML parser when validation is complete.
	 */
	public void endDocument() throws SAXException {
	}

	/**
	 * Called by the XML parser when a opening element is encountered.
	 */
	public void startElement(String namespaceURI, String lName, // local name
			String qName, // qualified name
			Attributes attrs) throws SAXException {
	}

	/**
	 * Called by the XML parser when a closing element is encountered.
	 */
	public void endElement(String namespaceURI, String sName, // simple name
			String qName // qualified name
	) throws SAXException {
	}

	/**
	 * Called by the XML parser when a character is encountered
	 */
	public void characters(char buf[], int offset, int len) throws SAXException {
	}

	/**
	 * Called by the XML parser when a processing instruction is encountered.
	 */
	public void processingInstruction(String target, String data)
			throws SAXException {
	}

	// ===========================================================
	// SAX ErrorHandler methods
	// ===========================================================

	/**
	 * Called by the XML parser when a validation error occurs.
	 */
	public void error(SAXParseException err) throws SAXParseException {
		String buffer = "";
		String message = err.getMessage();
		String tech = "";
		// Fix-up message
		int n = message.indexOf(":");
		if (n != -1) {
			tech = " (" + message.substring(0, n) + ")";
			message = message.substring(n + 1);
		}

		if (withHTML()) {
			buffer = "<a href=#" + err.getLineNumber() + ">Line "
					+ err.getLineNumber() + "</a>: " + message + tech;
		} else {
			buffer = "Line " + err.getLineNumber() + ": " + message + tech;
		}
		if (mLastError.compareTo(buffer) == 0)
			return; // Repeated error

		println(buffer);
		println();
		mLastError = buffer;
		mMarkLine.add(err.getLineNumber());
	}

	/**
	 * Called by the XML parser when a validation warning occurs.
	 */
	public void warning(SAXParseException err) throws SAXParseException {
		String buffer = "";
		if (withHTML()) {
			buffer = "<a href=#" + err.getLineNumber() + ">Line "
					+ err.getLineNumber() + "</a>: " + err.getMessage();
		} else {
			buffer = "Line " + err.getLineNumber() + ": " + err.getMessage();
		}
		if (mLastWarning.compareTo(buffer) == 0)
			return; // Repeated warning

		println(buffer);
		println();
		mLastWarning = buffer;
		mMarkLine.add(err.getLineNumber());
	}

	// ===========================================================
	// Utlity functions
	// ===========================================================
	/**
	 * Set the output writer to a PrintStream object.
	 */
	public void setWriter(PrintStream stream) {
		mWriter = null;
		mStream = stream;
	}

	/**
	 * Set the output writer to a JspWriter.
	 */
	public void setWriter(javax.servlet.jsp.JspWriter writer) {
		mWriter = writer;
		mStream = null;
	}

	/**
	 * Print a line of text to the currently active writer.
	 */
	public void println(String text) {
		try {
			if (mWriter != null)
				mWriter.println(text);
			if (mStream != null)
				mStream.println(text);
		} catch (Exception e) {
		}
	}

	/**
	 * Print text to the currently active writer.
	 */
	public void print(String text) {
		try {
			if (mWriter != null)
				mWriter.print(text);
			if (mStream != null)
				mStream.print(text);
		} catch (Exception e) {
		}
	}

	/**
	 * Print a blank line to the currently active writer.
	 */
	public void println() {
		try {
			if (mWriter != null)
				println("<br><br>");
			if (mStream != null)
				println("");
		} catch (Exception e) {
		}
	}

	/**
	 * Determines if output should be generated with HTML markup. Output to a
	 * JspWriter will have HTML mark-up since it will be used to generate a web
	 * page.
	 */
	public boolean withHTML() {
		if (mWriter != null)
			return true;
		return false;
	}

	/**
	 * Convert special HTML characters to the corresponding HTML entity.
	 */
	public static String fixHTML(String aTagFragment) {
		final StringBuffer result = new StringBuffer();

		final StringCharacterIterator iterator = new StringCharacterIterator(
				aTagFragment);
		char character = iterator.current();
		while (character != CharacterIterator.DONE) {
			switch (character) {
			case '<':
				result.append("&lt;");
				break;
			case '>':
				result.append("&gt;");
				break;
			case '\"':
				result.append("&quot;");
				break;
			case '\'':
				result.append("&#039;");
				break;
			case '\\':
				result.append("&#092;");
				break;
			case '&':
				result.append("&amp;");
				break;
			case ' ':
				result.append("&nbsp;");
				break;
			case '\t':
				result.append("&nbsp;&nbsp;&nbsp;");
				break;
			default:
				result.append(character);
				break;
			}
			character = iterator.next();
		}
		return result.toString();
	}

	/**
	 * Determine if a value is "checked" by looking at the value of the state.
	 * If the value is "YES" then the text <code>CHECKED</code> is returned,
	 * otherwise an empty string is returns.
	 */
	public String isChecked(String state) {
		if (state.compareToIgnoreCase("YES") == 0)
			return "CHECKED";
		return "";
	}

	/**
	 * Determine if a value is "checked" by looking at the value of the state.
	 * If the value is <code>true</code> then the text <code>CHECKED</code> is
	 * returned, otherwise an empty string is returns.
	 */
	public String isChecked(boolean state) {
		if (state)
			return "CHECKED";
		return "";
	}

	/**
	 * Determine if a value matches another value. If the values match then the
	 * text <code>CHECKED</code> is returned, otherwise an empty string is
	 * returns.
	 */
	public String isValue(String val1, String val2) {
		if (val1.compareToIgnoreCase(val2) == 0)
			return "CHECKED";
		return "";
	}

	/**
	 * Set the source location of the XML schema document.
	 */
	public void setXsdUrl(String xsdUrl) {
		mXsdUrl = xsdUrl;
	}

	/**
	 * Get the source location of the XML schema document.
	 */
	public String getXsdUrl() {
		return mXsdUrl;
	}

	/**
	 * Return version string with dots replaced by underscores.
	 */
	public String getVersionName() {
		return mXsdVersion.replace('.', '_');
	}

	// ========================================
	// Parameter passing
	// ========================================
	public void setUrl(String value) {
		mUrl = value;
	}

	public String getUrl() {
		return mUrl;
	}

	public String getUrlForm() {
		if (mUrl == null)
			return "";
		else
			return mUrl;
	}

	public void setVersion(String value) {
		mXsdVersion = value;
	}

	public String getVersion() {
		return mXsdVersion;
	}

	public void setCheckID(String value) {
		if (value == null) {
			mCheckID = true;
		} else {
			mCheckID = igpp.util.Text.isTrue(value);
		}
	}

	public boolean getCheckID() {
		return mCheckID;
	}

	public void setCheckURL(String value) {
		if (value == null) {
			mCheckURL = true;
		} else {
			mCheckURL = igpp.util.Text.isTrue(value);
		}
	}

	public boolean getCheckURL() {
		return mCheckURL;
	}

	/**
	 * Set the schema location based on version number to use.
	 */
	public void setSchemaFromVersion(String version) {
		version = version.replace(".", "_");
		mXsdUrl = "http://www.spase-group.org/data/schema/spase-" + version + ".xsd";
		loadSchema(mXsdUrl);
	}

	public void setFile(String value) {
		mFile = value;
	}

	public String getFile() {
		return mFile;
	}

	public String getFileForm() {
		if (mFile == null)
			return "";
		else
			return mFile;
	}
}