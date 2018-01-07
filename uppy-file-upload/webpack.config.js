var packageJSON = require('./package.json');
var ExtractTextPlugin = require('extract-text-webpack-plugin')
var path = require('path');
var webpack = require('webpack');

const PATHS = {
  build: path.join(__dirname, 'target', 'classes', 'META-INF', 'resources', 'webjars', packageJSON.name, packageJSON.version)
};

const loaders = {
  css: {
    loader: 'css-loader'
  }
}

module.exports = {
  entry: './app/index.js',
   module: {
    loaders: [
      {
        test: /\.css$/,
        use: ExtractTextPlugin.extract({
                 fallback: 'style-loader',
                 use: [loaders.css]
               })
      }
    ]
  },
  output: {
    path: PATHS.build,
    publicPath: '/assets/',
    filename: 'app-bundle.js'
  },
  plugins: [new ExtractTextPlugin('[name].css')]
};
