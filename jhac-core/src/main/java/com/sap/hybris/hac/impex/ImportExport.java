package com.sap.hybris.hac.impex;

import com.sap.hybris.hac.Base;
import com.sap.hybris.hac.Configuration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Import / export endpoint.
 *
 * @author Klaus Hauschild
 */
public class ImportExport extends Base<Impex, ImpexResult> {

  private static final String PATH = "/impex";
  private static final String IMPORT = "/import";
  private static final String EXPORT = "/export";

  public ImportExport(final Configuration configuration) {
    super(configuration, String.class);
  }

  public ImpexResult importData(final Impex impex) {
    final Object result = execute(impex, PATH + IMPORT, "");
    final String asString = result.toString();
    final Document resultHtml = Jsoup.parse(asString);

    final List<String> communicationErrors =
        resultHtml.select(".error").stream() //
            .map(Element::text) //
            .collect(Collectors.toList());
    if (!communicationErrors.isEmpty()) {
      throw new RestClientException(String.join("\n", communicationErrors));
    }

    final String error = getError(resultHtml);
    return new ImpexResult(error, emptyList());
  }

  private String getError(final Document resultHtml) {
    return resultHtml.select(".impexResult pre").text();
  }

  public ImpexResult exportData(final Impex impex) {
    final Object result = execute(impex, PATH + EXPORT, "");
    final String asString = result.toString();
    final Document resultHtml = Jsoup.parse(asString);

    final String error = getError(resultHtml);
    final ImpexResult errorResult = new ImpexResult(error, null);
    if (errorResult.hasError()) {
      return errorResult;
    }

    final List<String> exportResourceNames =
        resultHtml.select("#downloadExportResultData a").stream()
            .map(element -> element.attr("href"))
            .collect(Collectors.toList());

    final RestTemplate restTemplate = prepareRestTemplate(new HttpHeaders(), PATH + EXPORT);
    final List<byte[]> exportResources =
        exportResourceNames.stream()
            .map(
                exportResourceName ->
                    restTemplate
                        .exchange(
                            configuration().getEndpoint() + PATH + "/" + exportResourceName,
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            byte[].class)
                        .getBody())
            .collect(Collectors.toList());

    return new ImpexResult("", exportResources);
  }
}
