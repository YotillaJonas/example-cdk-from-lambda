package com.myorg;

import software.amazon.awscdk.App;

public class ExampleCdkFromLambdaApp {
    public static void main(final String[] args) {
        App app = new App();

        new ExampleCdkFromLambdaStack(app, "ExampleCdkFromLambdaStack");

        app.synth();
    }
}
