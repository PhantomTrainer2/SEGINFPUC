/**
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 * Uso:
 *   java DigestCalculator <Tipo_Digest> <Caminho_ArqListaDigest> <Caminho_da_Pasta_dos_Arquivos>
 *
 * Tipos de digest suportados: MD5, SHA1, SHA256, SHA512.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DigestCalculator {

    /**
     * Registro de um digest pertencente a um arquivo no catalogo XML.
     */
    private static class DigestRecord {
        String type;
        String hex;

        DigestRecord(String type, String hex) {
            this.type = type;
            this.hex = hex;
        }
    }

    /**
     * Registro de um arquivo no catalogo XML, associado ao seu elemento DOM.
     */
    private static class FileRecord {
        String fileName;
        List<DigestRecord> digests = new ArrayList<DigestRecord>();
        Element domElement;

        FileRecord(String fileName, Element domElement) {
            this.fileName = fileName;
            this.domElement = domElement;
        }

        DigestRecord getDigest(String type) {
            for (DigestRecord dr : digests) {
                if (isSameDigestType(dr.type, type)) {
                    return dr;
                }
            }
            return null;
        }
    }

    /**
     * Dados calculados para um arquivo presente na pasta informada.
     */
    private static class CalculatedFile {
        File file;
        String fileName;
        String digestHex;
        String status;

        CalculatedFile(File file, String fileName, String digestHex) {
            this.file = file;
            this.fileName = fileName;
            this.digestHex = digestHex;
        }
    }

    public static void main(String[] args) {
        // Validacao estrita dos argumentos de linha de comando
        if (args.length < 3) {
            printUsageAndExit("Erro: Argumentos insuficientes ou omitidos. Sao esperados exatamente 3 argumentos.");
        } else if (args.length > 3) {
            printUsageAndExit("Erro: Numero excessivo de argumentos fornecido. Sao esperados exatamente 3 argumentos.");
        }

        String inputDigestType = args[0].trim();
        String jcaAlgorithm = mapToJcaAlgorithm(inputDigestType);
        String canonicalType = mapToCanonicalType(inputDigestType);

        if (jcaAlgorithm == null || canonicalType == null) {
            printUsageAndExit("Erro: Tipo de digest invalido: '" + inputDigestType +
                    "'. Tipos aceitos: MD5, SHA1, SHA256, SHA512.");
        }

        File xmlFile = new File(args[1]);
        if (xmlFile.exists() && xmlFile.isDirectory()) {
            printUsageAndExit("Erro: O caminho do arquivo de digest especificado e um diretorio: " + args[1]);
        }

        File folder = new File(args[2]);
        if (!folder.exists() || !folder.isDirectory()) {
            printUsageAndExit("Erro: A pasta especificada nao existe ou nao e um diretorio: " + args[2]);
        }

        // Instanciacao do MessageDigest via JCA (Sun JCE)
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(jcaAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Erro: Algoritmo nao suportado pelo provedor JCA: " + jcaAlgorithm);
            System.exit(1);
            return;
        }

        // Leitura ou inicializacao do Documento XML do catalogo
        boolean originalHadXmlDeclaration = checkOriginalXmlDeclaration(xmlFile);
        Document doc;
        Element rootCatalog;
        List<FileRecord> xmlRecords = new ArrayList<FileRecord>();

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();

            if (xmlFile.exists() && xmlFile.length() > 0) {
                doc = db.parse(xmlFile);
                rootCatalog = doc.getDocumentElement();
                if (rootCatalog == null || !"CATALOG".equalsIgnoreCase(rootCatalog.getNodeName())) {
                    System.err.println("Erro: O arquivo XML informado nao possui a tag raiz <CATALOG>.");
                    System.exit(1);
                    return;
                }
                parseXmlRecords(rootCatalog, xmlRecords);
            } else {
                // Arquivo nao existe ou esta vazio: cria catalogo vazio
                doc = db.newDocument();
                rootCatalog = doc.createElement("CATALOG");
                doc.appendChild(rootCatalog);
            }
        } catch (ParserConfigurationException e) {
            System.err.println("Erro na configuracao do parser XML: " + e.getMessage());
            System.exit(1);
            return;
        } catch (SAXException e) {
            System.err.println("Erro ao realizar parse do arquivo XML: " + e.getMessage());
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("Erro de E/S ao ler o arquivo XML: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Listagem e ordenacao alfabetica dos arquivos regulares contidos na pasta
        File[] dirEntries = folder.listFiles();
        List<File> regularFiles = new ArrayList<File>();
        if (dirEntries != null) {
            for (File f : dirEntries) {
                if (f.isFile()) {
                    regularFiles.add(f);
                }
            }
        }
        Collections.sort(regularFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareTo(f2.getName());
            }
        });

        // 1. Calculo do digest para cada arquivo regular da pasta
        List<CalculatedFile> calculatedFiles = new ArrayList<CalculatedFile>();
        for (File f : regularFiles) {
            try {
                String digestHex = calculateFileDigest(f, messageDigest);
                calculatedFiles.add(new CalculatedFile(f, f.getName(), digestHex));
            } catch (IOException e) {
                System.err.println("Erro ao calcular digest do arquivo: " + f.getName() + " (" + e.getMessage() + ")");
            }
        }

        // 2. Determinacao de STATUS para cada arquivo conforme as regras de negocio e precedencia
        for (int i = 0; i < calculatedFiles.size(); i++) {
            CalculatedFile current = calculatedFiles.get(i);
            boolean collision = false;

            // Verificacao 1.1: Colisao com outro arquivo na pasta
            for (int j = 0; j < calculatedFiles.size(); j++) {
                if (i != j) {
                    CalculatedFile other = calculatedFiles.get(j);
                    if (current.digestHex.equalsIgnoreCase(other.digestHex)) {
                        collision = true;
                        break;
                    }
                }
            }

            // Verificacao 1.2: Colisao com arquivo de nome diferente cadastrado no XML
            if (!collision) {
                for (FileRecord rec : xmlRecords) {
                    if (!rec.fileName.equals(current.fileName)) {
                        for (DigestRecord dr : rec.digests) {
                            if (isSameDigestType(dr.type, canonicalType) && dr.hex.equalsIgnoreCase(current.digestHex)) {
                                collision = true;
                                break;
                            }
                        }
                        if (collision) {
                            break;
                        }
                    }
                }
            }

            if (collision) {
                current.status = "COLISION";
            } else {
                // Se nao houve colisao, buscar registro do proprio arquivo no XML
                FileRecord matchingRecord = findRecordByFileName(xmlRecords, current.fileName);
                if (matchingRecord != null) {
                    DigestRecord recordedDigest = matchingRecord.getDigest(canonicalType);
                    if (recordedDigest != null) {
                        if (recordedDigest.hex.equalsIgnoreCase(current.digestHex)) {
                            current.status = "OK";
                        } else {
                            current.status = "NOT OK";
                        }
                    } else {
                        current.status = "NOT FOUND";
                    }
                } else {
                    current.status = "NOT FOUND";
                }
            }
        }

        // 3. Impressao na saida padrao no formato estrito:
        //    Nome_Arq<SP>Tipo_Digest<SP>Digest_Hex_Arq<SP>(STATUS)
        for (CalculatedFile cf : calculatedFiles) {
            System.out.println(cf.fileName + " " + canonicalType + " " + cf.digestHex + " (" + cf.status + ")");
        }

        // 4. Atualizacao do XML com os digests dos arquivos com status NOT FOUND
        int notFoundCount = 0;
        for (CalculatedFile cf : calculatedFiles) {
            if ("NOT FOUND".equals(cf.status)) {
                notFoundCount++;
                FileRecord existingRecord = findRecordByFileName(xmlRecords, cf.fileName);
                if (existingRecord != null) {
                    // Arquivo ja existe no XML: adiciona DIGEST_ENTRY no FILE_ENTRY existente
                    Element digestEntry = doc.createElement("DIGEST_ENTRY");
                    Element typeElem = doc.createElement("DIGEST_TYPE");
                    typeElem.setTextContent(canonicalType);
                    Element hexElem = doc.createElement("DIGEST_HEX");
                    hexElem.setTextContent(cf.digestHex);
                    digestEntry.appendChild(typeElem);
                    digestEntry.appendChild(hexElem);

                    existingRecord.domElement.appendChild(digestEntry);
                    existingRecord.digests.add(new DigestRecord(canonicalType, cf.digestHex));
                } else {
                    // Arquivo nao existe no XML: cria novo FILE_ENTRY no final de CATALOG
                    Element fileEntry = doc.createElement("FILE_ENTRY");
                    Element nameElem = doc.createElement("FILE_NAME");
                    nameElem.setTextContent(cf.fileName);
                    fileEntry.appendChild(nameElem);

                    Element digestEntry = doc.createElement("DIGEST_ENTRY");
                    Element typeElem = doc.createElement("DIGEST_TYPE");
                    typeElem.setTextContent(canonicalType);
                    Element hexElem = doc.createElement("DIGEST_HEX");
                    hexElem.setTextContent(cf.digestHex);
                    digestEntry.appendChild(typeElem);
                    digestEntry.appendChild(hexElem);

                    fileEntry.appendChild(digestEntry);
                    rootCatalog.appendChild(fileEntry);

                    FileRecord newRecord = new FileRecord(cf.fileName, fileEntry);
                    newRecord.digests.add(new DigestRecord(canonicalType, cf.digestHex));
                    xmlRecords.add(newRecord);
                }
            }
        }

        // Persistencia do arquivo XML se foi atualizado ou se ainda nao existia
        boolean needsSave = !xmlFile.exists() || xmlFile.length() == 0 || notFoundCount > 0;
        if (needsSave) {
            try {
                // Cria diretorios pais caso nao existam
                File parentDir = xmlFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                writeXmlDocument(doc, xmlFile, !originalHadXmlDeclaration);
            } catch (TransformerException e) {
                System.err.println("Erro ao salvar o catalogo XML: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /**
     * Calcula o digest do conteudo do arquivo lendo em blocos com buffer de 8192 bytes
     * e atualizando o MessageDigest iterativamente via update(buffer, 0, bytesRead).
     */
    private static String calculateFileDigest(File file, MessageDigest md) throws IOException {
        md.reset();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }

        byte[] digest = md.digest();

        // Conversao estrita para hexadecimal conforme logica especificada pelo professor
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            String hex = Integer.toHexString(0x0100 + (digest[i] & 0x00FF)).substring(1);
            buf.append((hex.length() < 2 ? "0" : "") + hex);
        }
        return buf.toString();
    }

    /**
     * Realiza a leitura e mapeamento dos registros FILE_ENTRY e DIGEST_ENTRY da arvore DOM.
     */
    private static void parseXmlRecords(Element rootCatalog, List<FileRecord> records) {
        NodeList fileEntryNodes = rootCatalog.getElementsByTagName("FILE_ENTRY");
        for (int i = 0; i < fileEntryNodes.getLength(); i++) {
            Node fileNode = fileEntryNodes.item(i);
            if (fileNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element fileElem = (Element) fileNode;

            String fileName = null;
            NodeList fileNameNodes = fileElem.getElementsByTagName("FILE_NAME");
            if (fileNameNodes.getLength() > 0) {
                fileName = fileNameNodes.item(0).getTextContent().trim();
            }

            if (fileName == null || fileName.isEmpty()) {
                continue;
            }

            FileRecord record = findRecordByFileName(records, fileName);
            if (record == null) {
                record = new FileRecord(fileName, fileElem);
                records.add(record);
            }

            NodeList digestEntryNodes = fileElem.getElementsByTagName("DIGEST_ENTRY");
            for (int j = 0; j < digestEntryNodes.getLength(); j++) {
                Node digestNode = digestEntryNodes.item(j);
                if (digestNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element digestElem = (Element) digestNode;

                String digestType = null;
                NodeList typeNodes = digestElem.getElementsByTagName("DIGEST_TYPE");
                if (typeNodes.getLength() > 0) {
                    digestType = typeNodes.item(0).getTextContent().trim();
                }

                String digestHex = null;
                NodeList hexNodes = digestElem.getElementsByTagName("DIGEST_HEX");
                if (hexNodes.getLength() > 0) {
                    digestHex = hexNodes.item(0).getTextContent().trim();
                }

                if (digestType != null && digestHex != null) {
                    record.digests.add(new DigestRecord(digestType, digestHex));
                }
            }
        }
    }

    /**
     * Localiza um registro de arquivo na lista em memoria pelo seu nome.
     */
    private static FileRecord findRecordByFileName(List<FileRecord> records, String fileName) {
        for (FileRecord r : records) {
            if (r.fileName.equals(fileName)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Compara dois tipos de digest de forma insensivel a maiusculas/minusculas e hifens
     * (ex: "SHA1" e "SHA-1" sao equivalentes).
     */
    private static boolean isSameDigestType(String t1, String t2) {
        if (t1 == null || t2 == null) {
            return false;
        }
        String s1 = t1.trim().replace("-", "").toUpperCase();
        String s2 = t2.trim().replace("-", "").toUpperCase();
        return s1.equals(s2);
    }

    /**
     * Mapeia o tipo fornecido para o nome oficial do algoritmo na JCA (Sun JCE).
     */
    private static String mapToJcaAlgorithm(String type) {
        if (type == null) {
            return null;
        }
        String clean = type.trim().replace("-", "").toUpperCase();
        if ("MD5".equals(clean)) {
            return "MD5";
        } else if ("SHA1".equals(clean)) {
            return "SHA-1";
        } else if ("SHA256".equals(clean)) {
            return "SHA-256";
        } else if ("SHA512".equals(clean)) {
            return "SHA-512";
        }
        return null;
    }

    /**
     * Mapeia o tipo fornecido para o formato canonico de saida e persistencia no XML (sem hifen).
     */
    private static String mapToCanonicalType(String type) {
        if (type == null) {
            return null;
        }
        String clean = type.trim().replace("-", "").toUpperCase();
        if ("MD5".equals(clean)) {
            return "MD5";
        } else if ("SHA1".equals(clean)) {
            return "SHA1";
        } else if ("SHA256".equals(clean)) {
            return "SHA256";
        } else if ("SHA512".equals(clean)) {
            return "SHA512";
        }
        return null;
    }

    /**
     * Verifica se o arquivo XML original ja continha uma declaracao '<?xml'.
     */
    private static boolean checkOriginalXmlDeclaration(File file) {
        if (!file.exists() || file.length() == 0) {
            return false;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] header = new byte[128];
            int bytesRead = fis.read(header);
            if (bytesRead > 0) {
                String str = new String(header, 0, bytesRead, "UTF-8").trim();
                return str.startsWith("<?xml");
            }
        } catch (Exception ignored) {
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
        return false;
    }

    /**
     * Remove nos de texto em branco superfluos da arvore DOM para assegurar
     * uma indentacao XML limpa e uniforme gerada pelo Transformer.
     */
    private static void cleanEmptyTextNodes(Node node) {
        NodeList childNodes = node.getChildNodes();
        for (int i = childNodes.getLength() - 1; i >= 0; i--) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getNodeValue().trim().isEmpty()) {
                    node.removeChild(child);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                cleanEmptyTextNodes(child);
            }
        }
    }

    /**
     * Serializa a arvore DOM para o arquivo XML com indentacao padronizada de 4 espacos.
     */
    private static void writeXmlDocument(Document doc, File targetFile, boolean omitXmlDeclaration)
            throws TransformerException {
        cleanEmptyTextNodes(doc.getDocumentElement());

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        if (omitXmlDeclaration) {
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        } else {
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        }

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(targetFile);
        transformer.transform(source, result);
    }

    /**
     * Imprime mensagem de orientacao de execucao e encerra o programa com status 1.
     */
    private static void printUsageAndExit(String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            System.err.println(errorMessage);
        }
        System.err.println("Orientacao de Execucao:");
        System.err.println("  java DigestCalculator <Tipo_Digest> <Caminho_ArqListaDigest> <Caminho_da_Pasta_dos_Arquivos>");
        System.err.println("Argumentos:");
        System.err.println("  <Tipo_Digest>: Algoritmo a ser calculado (MD5, SHA1, SHA256 ou SHA512)");
        System.err.println("  <Caminho_ArqListaDigest>: Localizacao do arquivo XML com o catalogo de digests");
        System.err.println("  <Caminho_da_Pasta_dos_Arquivos>: Diretorio contendo os arquivos a processar");
        System.exit(1);
    }
}
