/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package ai.djl.basicdataset.utils;

import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.embedding.EmbeddingException;
import ai.djl.modality.nlp.embedding.TextEmbedding;
import ai.djl.modality.nlp.embedding.TrainableTextEmbedding;
import ai.djl.modality.nlp.embedding.TrainableWordEmbedding;
import ai.djl.modality.nlp.preprocess.LowerCaseConvertor;
import ai.djl.modality.nlp.preprocess.PunctuationSeparator;
import ai.djl.modality.nlp.preprocess.SimpleTokenizer;
import ai.djl.modality.nlp.preprocess.TextProcessor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@link TextData} is a utility for managing textual data within a {@link
 * ai.djl.training.dataset.Dataset}.
 *
 * <p>See {@link ai.djl.basicdataset.TextDataset} for an example.
 */
public class TextData {

    private List<TextProcessor> textProcessors;
    private TextEmbedding textEmbedding;
    private SimpleVocabulary vocabulary;
    private boolean trainEmbedding;
    private int embeddingSize;

    private List<List<String>> textData;
    private int size;

    /**
     * Constructs a new {@link TextData}.
     *
     * @param config the configuration for the {@link TextData}
     */
    public TextData(Configuration config) {
        this.textProcessors = config.textProcessors;
        this.textEmbedding = config.textEmbedding;
        this.trainEmbedding = config.trainEmbedding;
        this.embeddingSize = config.embeddingSize;
    }

    /**
     * Returns a good default {@link Configuration} to use for the constructor with defaults.
     *
     * @return a good default {@link Configuration} to use for the constructor with defaults
     */
    public static Configuration getDefaultConfiguration() {
        List<TextProcessor> defaultTextProcessors =
                Arrays.asList(
                        new SimpleTokenizer(),
                        new LowerCaseConvertor(Locale.ENGLISH),
                        new PunctuationSeparator());

        return new TextData.Configuration()
                .setEmbeddingSize(15)
                .setTrainEmbedding(false)
                .setTextProcessors(defaultTextProcessors);
    }

    /**
     * Embds the text at a given index to an NDList.
     *
     * <p>Follows an embedding strategy based on {@link #trainEmbedding}.
     *
     * @param index the index of the data to embed
     * @param manager the manager for the embedded array
     * @return the embedded array
     * @throws EmbeddingException if the value could not be embedded
     */
    public NDList embedText(long index, NDManager manager) throws EmbeddingException {
        int iindex = Math.toIntExact(index);
        NDList data = new NDList();

        List<String> sentenceTokens = textData.get(iindex);
        if (trainEmbedding) {
            data.add(textEmbedding.preprocessTextToEmbed(manager, sentenceTokens));
        } else {
            data.add(textEmbedding.embedText(manager, sentenceTokens));
        }
        return data;
    }

    /**
     * Preprocess the textData by providing the data from the dataset.
     *
     * @param newTextData the data from the dataset
     */
    public void preprocess(List<String> newTextData) {
        SimpleVocabulary.VocabularyBuilder vocabularyBuilder =
                new SimpleVocabulary.VocabularyBuilder();
        vocabularyBuilder.optMinFrequency(3);
        vocabularyBuilder.optReservedTokens(Arrays.asList("<pad>", "<bos>", "<eos>"));

        if (textData == null) {
            textData = new ArrayList<>();
        }
        for (String textDatum : newTextData) {
            List<String> tokens = Collections.singletonList(textDatum);
            for (TextProcessor processor : textProcessors) {
                tokens = processor.preprocess(tokens);
            }
            vocabularyBuilder.add(tokens);
            textData.add(tokens);
        }
        vocabulary = vocabularyBuilder.build();
        for (int i = 0; i < textData.size(); i++) {
            List<String> tokenizedTextDatum = textData.get(i);
            for (int j = 0; j < tokenizedTextDatum.size(); j++) {
                if (!vocabulary.isKnownToken(tokenizedTextDatum.get(j))) {
                    tokenizedTextDatum.set(j, vocabulary.getUnknownToken());
                }
            }
            textData.set(i, tokenizedTextDatum);
        }
        size = textData.size();
        if (textEmbedding == null) {
            textEmbedding =
                    new TrainableTextEmbedding(
                            new TrainableWordEmbedding(vocabulary, embeddingSize));
            trainEmbedding = true;
        }
    }

    /**
     * Sets the text processors.
     *
     * @param textProcessors the new textProcessors
     */
    public void setTextProcessors(List<TextProcessor> textProcessors) {
        this.textProcessors = textProcessors;
    }

    /**
     * Sets the textEmbedding to embed the data with.
     *
     * @param textEmbedding the textEmbedding
     */
    public void setTextEmbedding(TextEmbedding textEmbedding) {
        this.textEmbedding = textEmbedding;
    }

    /**
     * Gets the {@link TextEmbedding} used to embed the data with.
     *
     * @return the {@link TextEmbedding}
     */
    public TextEmbedding getTextEmbedding() {
        return textEmbedding;
    }

    /**
     * Sets whether to train the textEmbedding.
     *
     * @param trainEmbedding true to train the text embedding
     */
    public void setTrainEmbedding(boolean trainEmbedding) {
        this.trainEmbedding = trainEmbedding;
    }

    /**
     * Sets the default embedding size.
     *
     * @param embeddingSize the default embedding size
     */
    public void setEmbeddingSize(int embeddingSize) {
        this.embeddingSize = embeddingSize;
    }

    /**
     * Gets the {@link SimpleVocabulary} built while preprocessing the text data.
     *
     * @return the {@link SimpleVocabulary}
     */
    public SimpleVocabulary getVocabulary() {
        if (vocabulary == null) {
            throw new IllegalStateException(
                    "This method must be called after preprocess is called on this object");
        }
        return vocabulary;
    }

    /**
     * Returns the size of the data.
     *
     * @return the size of the data
     */
    public int getSize() {
        return size;
    }

    /**
     * The configuration for creating a {@link TextData} value in a {@link
     * ai.djl.training.dataset.Dataset}.
     */
    public static final class Configuration {

        private List<TextProcessor> textProcessors;
        private TextEmbedding textEmbedding;
        private Boolean trainEmbedding;
        private Integer embeddingSize;

        /**
         * Sets the {@link TextProcessor}s to use for the text data.
         *
         * @param textProcessors the {@link TextProcessor}s
         * @return this configuration
         */
        public Configuration setTextProcessors(List<TextProcessor> textProcessors) {
            this.textProcessors = textProcessors;
            return this;
        }

        /**
         * Sets the {@link TextEmbedding} to use to embed the text data.
         *
         * @param textEmbedding the {@link TextEmbedding}
         * @return this configuration
         */
        public Configuration setTextEmbedding(TextEmbedding textEmbedding) {
            this.textEmbedding = textEmbedding;
            return this;
        }

        /**
         * Sets whether to train the {@link TextEmbedding}.
         *
         * @param trainEmbedding true to train the {@link TextEmbedding}
         * @return this configuration
         */
        public Configuration setTrainEmbedding(boolean trainEmbedding) {
            this.trainEmbedding = trainEmbedding;
            return this;
        }

        /**
         * Sets the size for new {@link TextEmbedding}s.
         *
         * @param embeddingSize the embedding size
         * @return this configuration
         */
        public Configuration setEmbeddingSize(int embeddingSize) {
            this.embeddingSize = embeddingSize;
            return this;
        }

        /**
         * Updates this {@link Configuration} with the non-null values from another configuration.
         *
         * @param other the other configuration to use to update this
         * @return this configuration after updating
         */
        public Configuration update(Configuration other) {
            textProcessors = other.textProcessors != null ? other.textProcessors : textProcessors;
            textEmbedding = other.textEmbedding != null ? other.textEmbedding : textEmbedding;
            trainEmbedding = other.trainEmbedding != null ? other.trainEmbedding : trainEmbedding;
            embeddingSize = other.embeddingSize != null ? other.embeddingSize : embeddingSize;
            return this;
        }
    }
}
