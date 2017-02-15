package com.lifeonwalden.webpackage.compressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.javascript.jscomp.CustCommandLineRunner;
import com.lifeonwalden.webpackage.compressor.constant.HtmlAttributeName;
import com.yahoo.platform.yui.compressor.CssCompressor;


public class Compressor {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("没有指定配置文件。");
            return;
        }

        Document doc = null;
        File index = null;
        try {
            Properties properties = Properties.class.newInstance();
            properties.load(new FileInputStream(args[0]));
            String version = properties.getProperty("version.number", String.valueOf(System.currentTimeMillis()));
            String virtualPath = properties.getProperty("resources.virtual.path");
            String resourcesAbsolutePath = properties.getProperty("resources.absolute.path");
            String charset = properties.getProperty("charsetName", "UTF-8");

            index = new File(properties.getProperty("index.file"));
            doc = Jsoup.parse(index, charset);

            Elements linkElements = doc.getElementsByTag("link");
            File outputCssFile = new File(properties.getProperty("css.output.file").replace("${version}", version));
            String linkPath = null;
            boolean firstOne = true;
            for (Element link : linkElements) {
                if (HtmlAttributeName.CSS_TYPE.equalsIgnoreCase(link.attr("type")) || HtmlAttributeName.CSS_REL.equalsIgnoreCase(link.attr("rel"))) {
                    link.remove();
                    linkPath = link.attr("href");
                    if (null != virtualPath) {
                        linkPath = linkPath.replace(virtualPath, "");
                    }
                    File cssFile = new File(linkPath);
                    if (!cssFile.isAbsolute()) {
                        cssFile = new File(resourcesAbsolutePath.concat(linkPath));
                    }
                    InputStreamReader isr = new InputStreamReader(new FileInputStream(cssFile), charset);
                    CssCompressor compressor = new CssCompressor(isr);
                    isr.close();
                    OutputStreamWriter osw = null;
                    if (firstOne) {
                        osw = new OutputStreamWriter(new FileOutputStream(outputCssFile), charset);
                        firstOne = false;
                    } else {
                        osw = new OutputStreamWriter(new FileOutputStream(outputCssFile, true), charset);
                    }
                    compressor.compress(osw, -1);
                    osw.close();
                }
            }
            doc.head().append(
                            "<link href=\"".concat(properties.getProperty("index.output.css.path")).concat(outputCssFile.getName())
                                            .concat("\" rel=\"stylesheet\" type=\"text/css\" />"));

            Elements scriptElements = doc.getElementsByTag("script");
            String[] compressArgs = new String[scriptElements.size() + 2];
            File outputScriptFile = new File(properties.getProperty("js.output.file").replace("${version}", version));
            compressArgs[0] = "--js_output_file=".concat(outputScriptFile.getAbsolutePath());
            int idx = 1;
            String srcPath = null;
            for (Element script : scriptElements) {
                if (HtmlAttributeName.JS_TYPE.equalsIgnoreCase(script.attr("type"))) {
                    script.remove();
                    srcPath = script.attr("src");
                    if (null != virtualPath) {
                        srcPath = srcPath.replace(virtualPath, "");
                    }
                    File scriptFile = new File(srcPath);
                    if (!scriptFile.isAbsolute()) {
                        scriptFile = new File(resourcesAbsolutePath.concat(srcPath));
                    }
                    compressArgs[idx++] = scriptFile.getAbsolutePath();
                }
            }

            compressArgs[scriptElements.size() + 1] = "--process_jquery_primitives";
            doc.body().append(
                            "<script type=\"text/javascript\" src=\"".concat(properties.getProperty("index.output.js.path"))
                                            .concat(outputScriptFile.getName()).concat("\"></script>"));
            OutputStreamWriter fos = new OutputStreamWriter(new FileOutputStream(properties.getProperty("index.output")), charset);
            fos.write(doc.html());
            fos.flush();
            fos.close();
            CustCommandLineRunner.main(compressArgs);
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
