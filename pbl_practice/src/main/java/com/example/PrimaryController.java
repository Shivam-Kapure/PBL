package com.example;

import java.io.IOException;
import java.io.File;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ScrollPane;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Map;

public class PrimaryController {
    @FXML
    private VBox mainContainer;
    
    @FXML
    private VBox resultsContainer;
    
    @FXML
    private VBox plotsContainer;
    
    @FXML
    private VBox metricsContainer;
    
    @FXML
    private Label fileStatus;

    @FXML
    private void handleFileChoose() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        File selectedFile = fileChooser.showOpenDialog(mainContainer.getScene().getWindow());
        if (selectedFile != null) {
            fileStatus.setText("Analyzing file: " + selectedFile.getName());
            fileStatus.setVisible(true);
            runAnalysis(selectedFile.getAbsolutePath());
        }
    }

    private void runAnalysis(String filePath) {
        try {
            // Get the path to the Python script
            java.net.URL scriptUrl = getClass().getResource("/scripts/infant_mortality_analysis.py");
            if (scriptUrl == null) {
                throw new IOException("Could not find Python script in resources");
            }
            
            String scriptPath = new File(scriptUrl.toURI()).getAbsolutePath();
            
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, filePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                displayResults();
            } else {
                showError("Analysis failed: " + output.toString());
            }
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("python")) {
                errorMessage = "Python not found. Please make sure Python is installed and added to your PATH.";
            }
            showError(errorMessage);
        }
    }

    private void displayResults() {
        plotsContainer.getChildren().clear();
        metricsContainer.getChildren().clear();
        
        // Display plots
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
                    imageView.setFitWidth(700);
                    imageView.setPreserveRatio(true);
                    
                    Label title = new Label(formatPlotTitle(plotFile));
                    title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                    
                    VBox plotBox = new VBox(10);
                    plotBox.getChildren().addAll(title, imageView);
                    plotsContainer.getChildren().add(plotBox);
                }
            } catch (Exception e) {
                showError("Error loading plot: " + e.getMessage());
            }
        }
        
        // Display metrics
        try {
            File metricsFile = new File("output_plots/model_metrics.json");
            if (metricsFile.exists()) {
                String metricsJson = new String(Files.readAllBytes(metricsFile.toPath()));
                Type metricsType = new TypeToken<Map<String, Map<String, Double>>>(){}.getType();
                Map<String, Map<String, Double>> metrics = new Gson().fromJson(metricsJson, metricsType);
                
                Label metricsTitle = new Label("Model Performance Metrics");
                metricsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                metricsContainer.getChildren().add(metricsTitle);
                
                VBox metricsBox = new VBox(5);
                metricsBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 5;");
                
                for (Map.Entry<String, Map<String, Double>> entry : metrics.entrySet()) {
                    String modelName = entry.getKey();
                    Map<String, Double> modelMetrics = entry.getValue();
                    
                    Label modelLabel = new Label(String.format("%s:", modelName));
                    modelLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                    
                    Label metricsLabel = new Label(String.format(
                        "MAE: %.2f\nRMSE: %.2f\nR²: %.2f",
                        modelMetrics.get("MAE"),
                        modelMetrics.get("RMSE"),
                        modelMetrics.get("R2")
                    ));
                    metricsLabel.setStyle("-fx-font-size: 14px;");
                    
                    VBox modelBox = new VBox(5);
                    modelBox.getChildren().addAll(modelLabel, metricsLabel);
                    metricsBox.getChildren().add(modelBox);
                }
                
                metricsContainer.getChildren().add(metricsBox);
            }
        } catch (Exception e) {
            showError("Error loading metrics: " + e.getMessage());
        }
        
        resultsContainer.setVisible(true);
    }

    private String formatPlotTitle(String plotFile) {
        String name = new File(plotFile).getName().replace(".png", "").replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @FXML
    private void switchToPrediction() throws IOException {
        App.setRoot("prediction");
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
