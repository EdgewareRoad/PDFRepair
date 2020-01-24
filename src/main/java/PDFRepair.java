import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.transform.TransformerException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdfparser.PDFStreamParser;

/**
 * Creates a simple PDF/A document.
 */
public class PDFRepair
{
    private PDFRepair()
    {
    }
    
    public static void main(String[] args) throws IOException, TransformerException
    {
        if (args.length != 2)
        {
            System.err.println("usage: " + PDFRepair.class.getName() +
                    " <input-file> <output-file>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        try (PDDocument doc = PDDocument.load(new File(inputFile)))
        {
        	for (PDPage page : doc.getPages())
        	{
        		// Get the low level page contents for each page
	            COSDictionary cosDictionary = page.getCOSObject();
	            if (cosDictionary != null)
	            {
	            	// Note that, if we can't read the dictionary we merely ignore this page
	            	Object contents = cosDictionary.getDictionaryObject("Contents");
	            	if (contents != null && COSStream.class.isAssignableFrom(contents.getClass()))
	            	{
	            		// Similarly, if the contents aren't a COSStream, ignore and move on
			            COSStream cosStreamInput = (COSStream)cosDictionary.getDictionaryObject("Contents");
			            COSStream cosStreamOutput = new COSStream();
			            
			            InputStream rawInput = cosStreamInput.createInputStream();
			            String text = getContentStreamDocument(rawInput);
			            rawInput.close();
			            InputStream input = new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
			            OutputStream output = cosStreamOutput.createOutputStream();
		            	IOUtils.copy(input, output);
			            input.close();
			            output.close();
			            
			            cosDictionary.setItem("Contents", cosStreamOutput);
		            	}
	            }
        	}           
            doc.save(outputFile);
            doc.close();
        }
    }
    

    private static String getContentStreamDocument(InputStream inputStream)
    {
        StringBuffer docu = new StringBuffer();

        PDFStreamParser parser;
        try
        {
            parser = new PDFStreamParser(IOUtils.toByteArray(inputStream));
            parser.parse();
        }
        catch (IOException e)
        {
            return null;
        }

        parser.getTokens().forEach(obj -> writeToken(obj, docu));

        return docu.toString();
    }

    private static void writeToken(Object obj, StringBuffer docu)
    {
        if (obj instanceof Operator)
        {
            addOperators(obj, docu);
        }
        else
        {
            writeOperand(obj, docu);
        }
    }

    private static void writeOperand(Object obj, StringBuffer docu) 
    {
        if (obj instanceof COSName)
        {
            String str = "/" + ((COSName) obj).getName();
            docu.append(str + " ");
        }
        else if (obj instanceof COSBoolean)
        {
            String str = obj.toString();
            docu.append(str + " ");
        }
        else if (obj instanceof COSArray)
        {
            docu.append("[ ");
            for (COSBase elem : (COSArray) obj)
            {
                writeOperand(elem, docu);
            }
            docu.append("] ");
        }
        else if (obj instanceof COSString)
        {
            docu.append("(");
            COSString cosString = (COSString)obj;
            
            byte[] bytes = cosString.getBytes();
            for (byte b : bytes)
            {
                int chr = b & 0xff;
                if (chr < 0x20 || chr > 0x7e)
                {
                    // non-printable ASCII is shown as an octal escape
                    String str = String.format("\\%03o", chr);
                    docu.append(str);
                }
                else if (chr == '(' || chr == ')' || chr == '\n' || chr == '\r' ||
                         chr == '\t' || chr == '\b' || chr == '\f' || chr == '\\')
                {
                    // PDF reserved characters must be escaped
                    String str = "\\" + (char)chr;
                    docu.append(str);
                }
                else
                {
                    String str = Character.toString((char) chr);
                    docu.append(str);
                }
            }
            docu.append(") ");
        }
        else if (obj instanceof COSNumber)
        {
            String str;
            if (obj instanceof COSFloat)
            {
                str = Float.toString(((COSFloat) obj).floatValue());
            }
            else
            {
                str = Integer.toString(((COSNumber) obj).intValue());
            }
            docu.append(str + " ");
        }
        else if (obj instanceof COSDictionary)
        {
            docu.append("<< ");
            COSDictionary dict = (COSDictionary) obj;
            for (Map.Entry<COSName, COSBase> entry : dict.entrySet())
            {
                writeOperand(entry.getKey(), docu);
                writeOperand(entry.getValue(), docu);
            }
            docu.append(">> ");
        }
        else
        {
            String str = obj.toString();
            str = str.substring(str.indexOf('{') + 1, str.length() - 1);
            docu.append(str + " ");
        }
    }

    private static void addOperators(Object obj, StringBuffer docu) 
    {
        Operator op = (Operator) obj;

        if (op.getName().equals(OperatorName.BEGIN_INLINE_IMAGE))
        {
            docu.append(OperatorName.BEGIN_INLINE_IMAGE + "\n");
            COSDictionary dic = op.getImageParameters();
            for (COSName key : dic.keySet())
            {
                Object value = dic.getDictionaryObject(key);
                docu.append(key.getName() + " ");
                writeToken(value, docu);
                docu.append("\n");
            }
            String imageString = new String(op.getImageData(), StandardCharsets.ISO_8859_1);
            docu.append(OperatorName.BEGIN_INLINE_IMAGE_DATA + "\n");
            docu.append(imageString);
            docu.append("\n");
            docu.append(OperatorName.END_INLINE_IMAGE + "\n");
        }
        else
        {
            String operator = ((Operator) obj).getName();
            docu.append(operator + "\n");
        }
    }

}