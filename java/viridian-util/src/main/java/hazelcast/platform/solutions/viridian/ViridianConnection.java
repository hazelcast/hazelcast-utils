package hazelcast.platform.solutions.viridian;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.SSLConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class ViridianConnection {
    public static final String VIRIDIAN_SECRETS_DIR_PROP = "VIRIDIAN_SECRETS_DIR";
    public static final String VIRIDIAN_CLUSTER_ID_PROP = "VIRIDIAN_CLUSTER_ID";
    public static final String VIRIDIAN_PASSWORD_PROP = "VIRIDIAN_PASSWORD";
    public static final String VIRIDIAN_DISCOVERY_TOKEN_PROP = "VIRIDIAN_DISCOVERY_TOKEN";

    private static String getRequiredEnv(String envVarName){
        String result = System.getenv(envVarName);
        if (result == null)
            throw new RuntimeException("Required environment variable (" + envVarName + ") was not provided.");

        return result;
    }


    /**
     * Looks for the environment variables that are used to
     * specify Viridian connection information.
     * @return true if Viridian configuration is present, false otherwise
     */
    public static boolean viridianConfigPresent(){
        String secretsDir = System.getenv(VIRIDIAN_SECRETS_DIR_PROP);
        return secretsDir != null;
    }

    /**
     * Modifies the passed ClientConfig instance with the TLS paramters needed to
     * connect to Virdian based on the following environment variables.
     *
     * VIRIDIAN_SECRETS_DIR   The location of client.keystore and client.truststore.
     *                        If VIRIDIAN_SECRETS_DIR/config.yaml exists, all of the other TLS parameters will be
     *                        obtained from there.
     *
     * VIRIDIAN_CLUSTER_ID       The name of the target cluster within the Viridian account
     * VIRIDIAN_DISCOVERY_TOKEN  The Viridian discovery token for the targeted cluster
     * VIRIDIAN_PASSWORD         The password that protects the keystore and truststore
     *
     * @param clientConfig the base configuration that will be used to connect
     *                     to Viridian.  This instance will be modified!
     */
    public static void configureFromEnvironment(ClientConfig clientConfig){
        String secretsDir = getRequiredEnv(VIRIDIAN_SECRETS_DIR_PROP);
        String password;
        String discoveryToken;
        String clusterId;

        File configFile = new File(secretsDir,"config.yaml");
        if (configFile.isFile()){
            Yaml parser = new Yaml();
            try(FileInputStream fis = new FileInputStream(configFile)){
                Map<String, Object> config = parser.load(fis);
                Map<String, Object> clusterConfig = (Map<String, Object>) config.get("cluster");
                if (clusterConfig == null){
                    throw new RuntimeException("Configuration file at \"" + configFile.getAbsolutePath() +
                            "\"does not have the expected format");
                }
                clusterId = (String) clusterConfig.get("name");
                discoveryToken = (String) clusterConfig.get("discovery-token");
                if (clusterId == null || discoveryToken == null){
                    throw new RuntimeException("Configuration file at \"" + configFile.getAbsolutePath() +
                            "\"does not have the expected format");
                }

                Map<String, Object> sslConfig = (Map<String, Object>) config.get("ssl");
                if (sslConfig == null){
                    throw new RuntimeException("Configuration file at \"" + configFile.getAbsolutePath() +
                            "\"does not have the expected format");
                }

                password = (String) sslConfig.get("key-password");
                if (password == null){
                    throw new RuntimeException("Configuration file at \"" + configFile.getAbsolutePath() +
                            "\"does not have the expected format");
                }
            } catch (IOException e) {
                throw new RuntimeException("An error occurred while parsing \"" + configFile.getAbsolutePath() + "\"",e);
            }
        } else {
            password = getRequiredEnv(VIRIDIAN_PASSWORD_PROP);
            clusterId = getRequiredEnv(VIRIDIAN_CLUSTER_ID_PROP);
            discoveryToken = getRequiredEnv(VIRIDIAN_DISCOVERY_TOKEN_PROP);
        }

        configure(clusterId, discoveryToken, password, secretsDir, clientConfig);
    }

    /**
     * Modifies the passed ClientConfig instance with the TLS paramters needed to
     * connect to Virdian
     *
     * @param clusterId    the Viridian cluster id
     * @param discoveryToken the Viridian discovery token
     * @param password     the password for the trust store and key store
     * @param secretsDir   the directory containing the trust store and key store, which must be named
     *                     client.truststore" and "client.keystore" respectively.
     * @param clientConfig the base client connection configuration.  This instance will be modified!
     */
    public static void configure(String clusterId, String discoveryToken, String password, String secretsDir, ClientConfig clientConfig){
        File configDir = new File(secretsDir);
        if (!configDir.isDirectory()){
            throw new RuntimeException("Could not initialize Viridian connection because the given secrets directory (" + secretsDir + ") does not exist or is not a directory.");
        }

        File keyStoreFile = new File(configDir, "client.keystore");
        File trustStoreFile = new File(configDir, "client.truststore");
        if (!keyStoreFile.isFile() || !keyStoreFile.canRead()){
            throw new RuntimeException("The keystore file (" + keyStoreFile.getPath() +") was not found or could not be read.");
        }
        if (!trustStoreFile.isFile() || !trustStoreFile.canRead()){
            throw new RuntimeException("The truststore file (" + trustStoreFile.getPath() +") was not found or could not be read.");
        }

        Properties props = new Properties();
        props.setProperty("javax.net.ssl.keyStore", keyStoreFile.getPath());
        props.setProperty("javax.net.ssl.keyStorePassword", password);
        props.setProperty("javax.net.ssl.trustStore", trustStoreFile.getPath());
        props.setProperty("javax.net.ssl.trustStorePassword", password);

        clientConfig.getNetworkConfig().setSSLConfig(new SSLConfig().setEnabled(true).setProperties(props));
        clientConfig.getNetworkConfig().getCloudConfig().setEnabled(true).setDiscoveryToken(discoveryToken);
        clientConfig.setProperty("hazelcast.client.cloud.url", "https://api.viridian.hazelcast.com");
        clientConfig.setClusterName(clusterId);
    }
}
