package org.acme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@QuarkusMain
public class ReadPublicRepoApplication implements QuarkusApplication {
    private static final String GITHUB_USERNAME = "svilkata";
    private static final String GITHUB_REPO_NAME = "quarkus-demos";

//    private static final String GITHUB_ORG_NAME = "zerodt-io";
//    private static final String GITHUB_REPO_NAME = "sandbox";

    private static final String GITHUB_REPO_BRANCH = "main";
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public int run(String... args) throws Exception {

//		readFromRepoFileContentAndSaveFile();

        readFromRepoFilesListAndMetadataRecursively();

        return 0;
    }

    private void readFromRepoFilesListAndMetadataRecursively() throws IOException {
        /*
         * Call GitHub branches API REST end point & get JSON response. This response
         * will also provide URL with treeSha for Tree REST endpoint.
         */
        Map jsonMap = makeRESTCall("https://api.github.com/repos/%s/%s/branches/%s".formatted(GITHUB_USERNAME, GITHUB_REPO_NAME, GITHUB_REPO_BRANCH));
        System.out.println(
                "Branches API Response = \n<API RESPONSE START>\n " + gson.toJson(jsonMap) + "\n<API RESPONSE END>\n");


        /*
         * Fetch Tree API REST endpoint URL from above response. We will use gson tree
         * traversing methods to get this.
         *
         * Path in JSON = root > commit > commit > tree > url
         */
        String treeApiUrl = gson.toJsonTree(jsonMap).getAsJsonObject().get("commit").getAsJsonObject().get("commit")
                .getAsJsonObject().get("tree").getAsJsonObject().get("url").getAsString();
        System.out.println("TREE API URL = " + treeApiUrl + "\n");


        /*
         * Now call GitHub Tree API to get tree of files with metadata. Added recursive
         * parameter to get all files recursively.
         */
        Map jsonTreeMap = makeRESTCall(treeApiUrl + "?recursive=1");
        System.out.println(
                "TREE API Response = \n<API RESPONSE START>\n " + gson.toJson(jsonMap) + "\n<API RESPONSE END>\n");


        // Get tree list & iterate over it.
        System.out.println("Directory & files list :");
        for (Object obj : ((List) jsonTreeMap.get("tree"))) {

            Map fileMetadata = (Map) obj;

            // Type tree will be directory & blob will be file. Print files & directory
            // list with file sizes.
            if (fileMetadata.get("type").equals("tree")) {
                System.out.println("Directory = " + fileMetadata.get("path"));
            } else {
                System.out.println(
                        "File = " + fileMetadata.get("path") + " | Size = " + fileMetadata.get("size") + " Bytes");
            }
        }
    }

    /**
     * This method will make a REST GET call for this URL using Apache http client &
     * fluent library.
     * <p>
     * Then parse response using GSON & return parsed Map.
     */
    private Map makeRESTCall(String restUrl) throws IOException {
        Content content = Request.Get(restUrl).execute().returnContent();
        String jsonString = content.asString();
        System.out.println("content = " + jsonString);

        // To print response JSON, using GSON. Any other JSON parser can be used here.
        Map jsonMap = gson.fromJson(jsonString, Map.class);
        return jsonMap;
    }


    private static void readFromRepoFileContentAndSaveFile() throws IOException, URISyntaxException {
        /*
         * Call GitHub REST API - https://github.com/svilkata/quarkus-demos
         *
         * Using REST client library to simplify REST call.
         */
        Client restClient = ClientBuilder.newClient();
        List<Map> response = restClient
                .target("https://api.github.com/repos/%s/%s/contents?ref=%s".formatted(GITHUB_USERNAME, GITHUB_REPO_NAME, GITHUB_REPO_BRANCH))
                .request()
                .get(List.class);

//		List<Map> response = restClient.getForObject(
//				"https://api.github.com/repos/{owner}/{repo}/contents?ref={branch}", List.class,
//				"svilkata", "quarkus-demos", "main");

        // To print response JSON, using GSON. Any other JSON parser can be used here.
        System.out.println("<JSON RESPONSE START>\n" + gson.toJson(response) + "\n<JSON RESPONSE END>\n");

        // Iterate through list of file metadata from response.
        for (Map fileMetaData : response) {

            // Get file name & raw file download URL from response.
            String fileName = (String) fileMetaData.get("name");
            String downloadUrl = (String) fileMetaData.get("download_url");
            System.out.println("File Name = " + fileName + " | Download URL = " + downloadUrl);


            // We will only fetch read me file for this example.
            if (downloadUrl != null && downloadUrl.contains("README")) {
                /*
                 * Get file content as string
                 *
                 * Using Apache commons IO to read content from the remote URL. Any other HTTP
                 * client library can be used here.
                 */
                String fileContent = IOUtils.toString(new URI(downloadUrl), StandardCharsets.UTF_8);
                System.out.println("\nfileContent = <FILE CONTENT START>\n" + fileContent + "\n<FILE CONTENT END>\n");

                /*
                 * Download read me file to local.
                 *
                 * Using Apache commons IO to create file from remote content. Any other library
                 * or code can be written to get content from URL & create file in local.
                 */
                File file = new File("github-api-downloaded-" + fileName);
                FileUtils.copyURLToFile(new URL(downloadUrl), file);
            }
        }
    }
}
