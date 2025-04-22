import pandas as pd
import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
from pandas.plotting import parallel_coordinates
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor, AdaBoostRegressor, ExtraTreesRegressor
from sklearn.svm import SVR
from sklearn.linear_model import LinearRegression, Ridge, Lasso, ElasticNet
from sklearn.tree import DecisionTreeRegressor
from sklearn.neighbors import KNeighborsRegressor
from sklearn.feature_selection import RFE
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GridSearchCV
import matplotlib
matplotlib.use('Agg')  # Use Agg backend for non-interactive plotting
import os
import json
import joblib

def run_analysis(data_path):
    # Load Data
    df = pd.read_csv(data_path)
    df = df.loc[:, ~df.columns.str.contains('Unnamed')]
    df_numeric = df.select_dtypes(include=[np.number])

    # Drop missing target values
    df_cleaned = df.dropna(subset=["Infant mortality rate (per 1000 live births)"])

    top_features = df_numeric.corr()["Infant mortality rate (per 1000 live births)"].abs().sort_values(ascending=False).index[1:11]

    # Create output directory if it doesn't exist
    output_dir = "output_plots"
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    # Generate and save plots
    # 1. Histogram for Feature Distributions
    fig, axes = plt.subplots(nrows=5, ncols=2, figsize=(16, 20), constrained_layout=True)
    axes = axes.flatten()

    for i, feature in enumerate(top_features):
        sns.histplot(df_cleaned[feature], bins=20, ax=axes[i], color='skyblue', edgecolor='black')
        axes[i].set_title(feature, fontsize=12)
        axes[i].tick_params(axis='x', rotation=45)

    for j in range(i + 1, len(axes)):
        fig.delaxes(axes[j])

    plt.savefig(os.path.join(output_dir, 'feature_distributions.png'))
    plt.close()

    # 2. Correlation Heatmap
    plt.figure(figsize=(14, 10))
    sns.heatmap(df_cleaned[top_features.tolist() + ["Infant mortality rate (per 1000 live births)"]].corr(), 
                annot=True, cmap="coolwarm", fmt=".2f", linewidths=0.5, cbar=True, 
                annot_kws={"size": 10}, square=True)
    plt.title("Correlation Heatmap (Top Features)", fontsize=14)
    plt.xticks(fontsize=10, rotation=45, ha='right')
    plt.yticks(fontsize=10)
    plt.savefig(os.path.join(output_dir, 'correlation_heatmap.png'))
    plt.close()

    # 3. Feature Importance Plot
    rf_model = RandomForestRegressor(n_estimators=100, random_state=42)
    rf_model.fit(df_cleaned[top_features], df_cleaned["Infant mortality rate (per 1000 live births)"])
    feature_importances = rf_model.feature_importances_

    plt.figure(figsize=(12, 6))
    sns.barplot(x=feature_importances, y=top_features, hue=top_features, palette="coolwarm", legend=False)
    plt.xlabel("Feature Importance")
    plt.ylabel("Features")
    plt.title("The Effect On Infant Mortality Rate")
    plt.savefig(os.path.join(output_dir, 'feature_importance.png'))
    plt.close()

    # Prepare data for model training
    X = df_cleaned[top_features]
    y = df_cleaned["Infant mortality rate (per 1000 live births)"]
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    
    # Scale the features
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    
    # Save the scaler for later use in prediction
    joblib.dump(scaler, os.path.join(output_dir, 'scaler.joblib'))
    
    # Initialize models
    models = {
        'Random Forest': RandomForestRegressor(n_estimators=100, random_state=42),
        'Gradient Boosting': GradientBoostingRegressor(n_estimators=100, random_state=42),
        'SVR': SVR(kernel='rbf'),
        'Linear Regression': LinearRegression(),
        'Ridge Regression': Ridge(alpha=1.0),
        'Lasso Regression': Lasso(alpha=1.0),
        'ElasticNet': ElasticNet(alpha=1.0, l1_ratio=0.5),
        'Decision Tree': DecisionTreeRegressor(random_state=42),
        'KNN': KNeighborsRegressor(n_neighbors=5),
        'AdaBoost': AdaBoostRegressor(n_estimators=100, random_state=42),
        'Extra Trees': ExtraTreesRegressor(n_estimators=100, random_state=42)
    }
    
    # Train models and collect metrics
    metrics = {}
    for name, model in models.items():
        model.fit(X_train_scaled, y_train)
        y_pred = model.predict(X_test_scaled)
        
        metrics[name] = {
            'MAE': mean_absolute_error(y_test, y_pred),
            'RMSE': np.sqrt(mean_squared_error(y_test, y_pred)),
            'R2': r2_score(y_test, y_pred)
        }
        
        # Save the best performing model
        if name == 'Random Forest':  # Using Random Forest as default
            joblib.dump(model, os.path.join(output_dir, 'best_model.joblib'))
    
    # Save feature names for prediction
    with open(os.path.join(output_dir, 'feature_names.json'), 'w') as f:
        json.dump(list(top_features), f)
    
    # Save metrics to a file
    with open(os.path.join(output_dir, 'model_metrics.json'), 'w') as f:
        json.dump(metrics, f)
    
    # Create model comparison plot
    plt.figure(figsize=(12, 6))
    model_names = list(metrics.keys())
    r2_scores = [metrics[name]['R2'] for name in model_names]
    
    plt.bar(model_names, r2_scores)
    plt.xticks(rotation=45, ha='right')
    plt.ylabel('R2 Score')
    plt.title('Model Comparison (R2 Scores)')
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'model_comparison.png'))
    plt.close()

    # Return the paths of generated plots and metrics
    return {
        'feature_distributions': os.path.join(output_dir, 'feature_distributions.png'),
        'correlation_heatmap': os.path.join(output_dir, 'correlation_heatmap.png'),
        'feature_importance': os.path.join(output_dir, 'feature_importance.png'),
        'model_comparison': os.path.join(output_dir, 'model_comparison.png'),
        'metrics': metrics
    }

if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        data_path = sys.argv[1]
        run_analysis(data_path) 