import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.preflight.*;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;

/**
 * 
 */

/**
 * Test of PDFRepair
 */
public class PDFRepairTest {
	
	File _inputFile, _outputFile;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception
	{
		_inputFile = File.createTempFile("PDFRepairTest-input-", ".pdf");
		_outputFile = File.createTempFile("PDFRepairTest-output-", ".pdf");
		
		File _fontFile = new File(getClass().getClassLoader().getResource("OCRAEXT.TTF").getFile());

        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);

            // load the font as this needs to be embedded
            PDFont font = PDType0Font.load(doc, _fontFile);

            // A PDF/A file needs to have the font embedded if the font is used for text rendering
            // in rendering modes other than text rendering mode 3.
            //
            // This requirement includes the PDF standard fonts, so don't use their static PDFType1Font classes such as
            // PDFType1Font.HELVETICA.
            //
            // As there are many different font licenses it is up to the developer to check if the license terms for the
            // font loaded allows embedding in the PDF.
            // 
            if (!font.isEmbedded())
            {
            	throw new IllegalStateException("PDF/A compliance requires that all fonts used for"
            			+ " text rendering in rendering modes other than rendering mode 3 are embedded.");
            }
            
            // create a page with the message
            try (PDPageContentStream contents = new PDPageContentStream(doc, page))
            {
                contents.beginText();
                contents.setFont(font, 12);
                contents.newLineAtOffset(100, 700);
                contents.showText("Test document for PDFRepair");
                contents.endText();
            }

            // add XMP metadata
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            
            try
            {
                DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
                dc.setTitle(_inputFile.getName());
                
                PDFAIdentificationSchema id = xmp.createAndAddPFAIdentificationSchema();
                id.setPart(1);
                id.setConformance("B");
                
                XmpSerializer serializer = new XmpSerializer();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                serializer.serialize(xmp, baos, true);

                PDMetadata metadata = new PDMetadata(doc);
                metadata.importXMPMetadata(baos.toByteArray());
                doc.getDocumentCatalog().setMetadata(metadata);
            }
            catch(BadFieldValueException e)
            {
                // won't happen here, as the provided value is valid
                throw new IllegalArgumentException(e);
            }

            // sRGB output intent
            InputStream colorProfile = getClass().getClassLoader().getResourceAsStream(
                    "sRGB.icc");
            PDOutputIntent intent = new PDOutputIntent(doc, colorProfile);
            intent.setInfo("sRGB IEC61966-2.1");
            intent.setOutputCondition("sRGB IEC61966-2.1");
            intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            intent.setRegistryName("http://www.color.org");
            doc.getDocumentCatalog().addOutputIntent(intent);

            doc.save(_inputFile);
        }
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void test() throws Exception
	{
		String[] args = { _inputFile.getAbsolutePath(), _outputFile.getAbsolutePath() };
		PDFRepair.main(args);
		
		PreflightParser parser = new PreflightParser(_outputFile);
		parser.parse(Format.PDF_A1A);
		PreflightDocument document = parser.getPreflightDocument();
		document.validate();
		ValidationResult result = document.getResult();
		// This is our only test at the moment and guarantees that PDFRepair hasn't broken anything
		assertTrue("Output file is not PDF_A1A", result.isValid());
	}

}
