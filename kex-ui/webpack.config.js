const path = require('path');
const webpack = require("webpack");
const MiniCssExtractPlugin = require("mini-css-extract-plugin");

module.exports = {
    mode: 'production',
    entry: {
        index: './src/main/resources/visual.js',
        //graph: './src/main/js/visual.js'
    },
    plugins: [
        new webpack.ProvidePlugin({
            $: "jquery",
            jQuery: "jquery"
        }),
        new MiniCssExtractPlugin({filename: "style.css"})
    ],
    output: {
        path: path.resolve(__dirname, './src/main/resources'),
        filename: '[name].js',
    },
    module: {
        rules: [
            {
                test: /\.css$/,
                use: [
                    MiniCssExtractPlugin.loader,
                    {loader: "css-loader"}
                ],
            }
        ]
    }
};