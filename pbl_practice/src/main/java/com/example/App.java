package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Window;
import javafx.geometry.Pos;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;
    private VBox mainContainer;
    private ScrollPane scrollPane;
    private ExecutorService executorService;

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    @Override
    public void start(Stage stage) throws IOException {
        executorService = Executors.newSingleThreadExecutor();
        scene = new Scene(loadFXML("primary"), 800, 600);
        stage.setScene(scene);
        stage.setTitle("Infant Mortality Analysis");
        stage.show();
    }

    private void chooseFile(Window window) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile != null) {
            runAnalysis(selectedFile.getAbsolutePath());
        }
    }

    private void runAnalysis(String filePath) {
        mainContainer.getChildren().clear();
        mainContainer.getChildren().add(new Label("Running analysis..."));
        
        executorService.submit(() -> {
            try {
                // Get the path to the Python script
                java.net.URL scriptUrl = getClass().getResource("/scripts/infant_mortality_analysis.py");
                if (scriptUrl == null) {
                    throw new IOException("Could not find Python script in resources");
                }
                
                String scriptPath = new File(scriptUrl.toURI()).getAbsolutePath();
                System.out.println("Script path: " + scriptPath); // Debug print
                
                // Create ProcessBuilder to run Python script
                ProcessBuilder pb = new ProcessBuilder("python", scriptPath, filePath);
                pb.redirectErrorStream(true);
                
                // Print working directory for debugging
                System.out.println("Working directory: " + pb.directory());
                System.out.println("Command: " + pb.command());
                
                Process process = pb.start();
                
                // Read the output
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
                );
                
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Script output: " + line); // Debug print
                    output.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    // Analysis completed successfully
                    javafx.application.Platform.runLater(() -> {
                        displayResults();
                    });
                } else {
                    final String errorOutput = output.toString();
                    javafx.application.Platform.runLater(() -> {
                        showError("Analysis failed. Error details:\n" + errorOutput);
                    });
                }
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage.contains("python")) {
                    errorMessage = "Python not found. Please make sure Python is installed and added to your PATH.\n" +
                                 "Try running 'python --version' in your command prompt to verify Python installation.";
                }
                final String finalError = errorMessage;
                javafx.application.Platform.runLater(() -> {
                    showError("Error running analysis: " + finalError);
                });
            }
        });
    }

    private void displayResults() {
        mainContainer.getChildren().clear();
        
        // Create buttons container
        HBox buttonsContainer = new HBox(10);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Create buttons
        Button chooseFileButton = new Button("Choose New CSV File");
        chooseFileButton.setOnAction(e -> chooseFile(scene.getWindow()));
        
        Button predictionButton = new Button("Go to Prediction");
        predictionButton.setOnAction(e -> {
            try {
                setRoot("prediction");
            } catch (IOException ex) {
                showError("Error loading prediction page: " + ex.getMessage());
            }
        });
        
        buttonsContainer.getChildren().addAll(chooseFileButton, predictionButton);
        mainContainer.getChildren().add(buttonsContainer);
        
        // Display the generated plots
        String[] plotFiles = {
            "output_plots/feature_distributions.png",
            "output_plots/correlation_heatmap.png",
            "output_plots/feature_importance.png",
            "output_plots/model_comparison.png"
        };
        
        for (String plotFile : plotFiles) {
            try {
                File file = new File(plotFile);
                if (file.exists()) {
                    Image image = new Image(file.toURI().toString());
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setFitWidth(750);
                    
                    VBox imageContainer = new VBox(5);
                    imageContainer.getChildren().addAll(
                        new Label(file.getName().replace(".png", "").replace("_", " ").toUpperCase()),
                        imageView
                    );
                    
                    mainContainer.getChildren().add(imageContainer);
                }
            } catch (Exception e) {
                showError("Error loading plot: " + e.getMessage());
            }
        }
        
        // Display model metrics if available
        try {
            File metricsFile = new File("output_plots/model_metrics.json");
            if (metricsFile.exists()) {
                String metricsJson = new String(Files.readAllBytes(metricsFile.toPath()));
                Type metricsType = new TypeToken<Map<String, Map<String, Double>>>(){}.getType();
                Map<String, Map<String, Double>> metrics = new Gson().fromJson(metricsJson, metricsType);
                
                VBox metricsContainer = new VBox(5);
                metricsContainer.getChildren().add(new Label("Model Performance Metrics:"));
                
                for (Map.Entry<String, Map<String, Double>> entry : metrics.entrySet()) {
                    String modelName = entry.getKey();
                    Map<String, Double> modelMetrics = entry.getValue();
                    
                    String metricsText = String.format("%s:\n  MAE: %.2f\n  RMSE: %.2f\n  R²: %.2f",
                        modelName,
                        modelMetrics.get("MAE"),
                        modelMetrics.get("RMSE"),
                        modelMetrics.get("R2"));
                    
                    metricsContainer.getChildren().add(new Label(metricsText));
                }
                
                mainContainer.getChildren().add(metricsContainer);
            }
        } catch (Exception e) {
            showError("Error loading metrics: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Analysis Error");
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) {
        launch();
    }

}