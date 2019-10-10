/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package ai.djl.integration.tests;

import ai.djl.Model;
import ai.djl.integration.util.Assertions;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataDesc;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.LayoutType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.Parameter;
import ai.djl.nn.convolutional.Conv1D;
import ai.djl.nn.convolutional.Conv2D;
import ai.djl.nn.convolutional.Conv3D;
import ai.djl.nn.core.Embedding;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.BatchNorm;
import ai.djl.nn.norm.Dropout;
import ai.djl.nn.recurrent.GRU;
import ai.djl.nn.recurrent.LSTM;
import ai.djl.nn.recurrent.RNN;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.Trainer;
import ai.djl.training.TrainingConfig;
import ai.djl.training.initializer.Initializer;
import ai.djl.util.PairList;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BlockCoreTest {

    @Test
    public void testLinear() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        long outSize = 3;
        Block block = new Linear.Builder().setOutChannels(outSize).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(2, 2);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.create(new float[] {1, 2, 3, 4}, inputShape);

                NDArray outBias = trainer.forward(new NDList(input)).head();
                NDArray expectedBias =
                        input.dot(manager.ones(new Shape(outSize, 2)).transpose())
                                .add(manager.zeros(new Shape(2, outSize)));
                Assert.assertEquals(outBias, expectedBias);

                testEncode(manager, block);
            }
        }

        block = new Linear.Builder().setOutChannels(outSize).setBias(false).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(2, 2);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.create(new float[] {1, 2, 3, 4}, inputShape);

                NDArray outNoBias = trainer.forward(new NDList(input)).head();
                NDArray expectedNoBias = input.dot(manager.ones(new Shape(outSize, 2)).transpose());
                Assert.assertEquals(outNoBias, expectedNoBias);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testLinearWithDefinedLayout() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        long outSize = 3;
        Block block = new Linear.Builder().setOutChannels(outSize).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape =
                        new Shape(
                                new long[] {2, 2},
                                new LayoutType[] {LayoutType.BATCH, LayoutType.CHANNEL});
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.create(new float[] {1, 2, 3, 4}, inputShape);

                NDArray outBias = trainer.forward(new NDList(input)).head();
                NDArray expectedBias =
                        input.dot(manager.ones(new Shape(outSize, 2)).transpose())
                                .add(manager.zeros(new Shape(2, outSize)));
                Assert.assertEquals(outBias, expectedBias);

                testEncode(manager, block);
            }
        }

        block = new Linear.Builder().setOutChannels(outSize).setBias(false).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape =
                        new Shape(
                                new long[] {2, 2},
                                new LayoutType[] {LayoutType.BATCH, LayoutType.CHANNEL});
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.create(new float[] {1, 2, 3, 4}, inputShape);

                NDArray outNoBias = trainer.forward(new NDList(input)).head();
                NDArray expectedNoBias = input.dot(manager.ones(new Shape(outSize, 2)).transpose());
                Assert.assertEquals(outNoBias, expectedNoBias);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testBatchNorm() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block = new BatchNorm.Builder().setAxis(1).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(2, 2);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.create(new float[] {1, 2, 3, 4}, inputShape);
                NDArray expected = manager.create(new float[] {1, 2, 3, 4}, inputShape);

                NDArray out = trainer.forward(new NDList(input)).head();
                Assertions.assertAlmostEquals(out, expected);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testDropout() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block = new Dropout.Builder().setProbability(.5f).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(2, 2);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();

                NDArray input = manager.create(new float[] {1, 2, 3, 4}, inputShape);
                NDArray out = trainer.forward(new NDList(input)).head();
                Assert.assertTrue(out.lte(out).all());

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testEmbedding() {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Embedding<Character> block =
                new Embedding.Builder<Character>()
                        .setItems(Arrays.asList('a', 'b', 'c'))
                        .setEmbeddingSize(2)
                        .build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);
            model.setDataType(DataType.INT32);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(2);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();

                // TODO: use trainer.forward
                Assert.assertEquals(block.forward(manager, 'x'), manager.create(new int[] {1, 1}));
                Assert.assertEquals(
                        block.forward(manager, new Character[] {'a', 'b'}),
                        manager.create(new int[] {1, 1, 1, 1}, new Shape(2, 2)));
            }
        }
    }

    @Test
    public void testConv1D() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block =
                new Conv1D.Builder()
                        .setKernel(new Shape(2))
                        .setNumFilters(1)
                        .setBias(false)
                        .build();

        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(1, 4, 4);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input =
                        manager.create(
                                new float[] {9, 8, 3, 6, 1, 4, 9, 7, 5, 11, 2, 5, 13, 10, 8, 4},
                                inputShape);
                NDArray expected = manager.create(new float[] {61, 55, 44}, new Shape(1, 1, 3));
                NDArray out = trainer.forward(new NDList(input)).get(0);
                Assert.assertEquals(out, expected);

                Shape[] outputShape = block.getOutputShapes(manager, new Shape[] {inputShape});
                Assert.assertEquals(out.getShape(), outputShape[0]);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testConv2D() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block = new Conv2D.Builder().setKernel(new Shape(2, 2)).setNumFilters(1).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(1, 1, 4, 4);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input =
                        manager.create(
                                new float[] {9, 8, 3, 6, 1, 4, 9, 7, 5, 11, 2, 5, 13, 10, 8, 4},
                                inputShape);
                NDArray expected =
                        manager.create(
                                new float[] {22, 24, 25, 21, 26, 23, 39, 31, 19},
                                new Shape(1, 1, 3, 3));

                NDArray out = trainer.forward(new NDList(input)).get(0);
                Assertions.assertAlmostEquals(out, expected);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testConv3D() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block = new Conv3D.Builder().setKernel(new Shape(2, 2, 2)).setNumFilters(1).build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(1, 1, 3, 3, 3);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input =
                        manager.create(
                                new float[] {
                                    9, 8, 3, 6, 1, 4, 9, 7, 5, 11, 2, 5, 13, 10, 8, 4, 4, 9, 7, 5,
                                    11, 2, 5, 13, 10, 8, 4
                                },
                                inputShape);
                NDArray expected =
                        manager.create(
                                new float[] {60, 41, 54, 48, 55, 59, 56, 61},
                                new Shape(1, 1, 2, 2, 2));

                NDArray out = trainer.forward(new NDList(input)).get(0);
                Assert.assertEquals(out, expected);

                Shape[] outputShape =
                        block.getOutputShapes(manager, new Shape[] {new Shape(1, 1, 3, 3, 3)});
                Assert.assertEquals(out.getShape(), outputShape[0]);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testRNNTanh() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block =
                new RNN.Builder()
                        .setStateSize(5)
                        .setNumStackedLayers(1)
                        .setActivation(RNN.Activation.TANH)
                        .build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(3, 4, 4);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.arange(0, 48, 1).reshape(inputShape);
                NDList outputs = trainer.forward(new NDList(input));
                NDArray out = outputs.get(0);
                Assertions.assertAlmostEquals(out, manager.ones(new Shape(3, 4, 5)));

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testRNNRelu() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block =
                new RNN.Builder()
                        .setStateSize(5)
                        .setNumStackedLayers(1)
                        .setActivation(RNN.Activation.RELU)
                        .build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(1, 2, 4);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.arange(0, 8, 1).reshape(inputShape);
                NDList outputs = trainer.forward(new NDList(input));
                NDArray out = outputs.get(0);
                NDArray expected =
                        manager.create(
                                new float[] {7, 12, 12, 12, 12, 23, 28, 28, 28, 28},
                                new Shape(1, 2, 5));
                Assert.assertEquals(out, expected);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testLstm() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        Block block =
                new LSTM.Builder()
                        .setStateSize(4)
                        .setNumStackedLayers(1)
                        .setActivation(RNN.Activation.RELU)
                        .build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(1, 2, 4);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();
                NDArray input = manager.arange(0, 8, 1).reshape(inputShape);
                NDList outputs = trainer.forward(new NDList(input));
                NDArray out = outputs.get(0);
                NDArray expected =
                        manager.create(
                                new float[] {
                                    0.9638f, 0.9631f, 0.9576f, 0.964f, 0.9639f, 0.964f, 0.9576f,
                                    0.964f
                                },
                                new Shape(1, 2, 4));
                Assertions.assertAlmostEquals(out, expected);

                testEncode(manager, block);
            }
        }
    }

    @Test
    public void testGRU() throws IOException {
        TrainingConfig config = new DefaultTrainingConfig(Initializer.ONES);

        GRU block =
                new GRU.Builder()
                        .setStateSize(4)
                        .setNumStackedLayers(1)
                        .setActivation(RNN.Activation.RELU)
                        .build();
        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            try (Trainer trainer = model.newTrainer(config)) {
                Shape inputShape = new Shape(1, 2, 4);
                trainer.initialize(new DataDesc[] {new DataDesc(inputShape)});

                NDManager manager = trainer.getManager();

                NDArray input = manager.arange(0, 8, 1).reshape(inputShape);

                NDList outputs = trainer.forward(new NDList(input));
                NDArray out = outputs.get(0);
                Assertions.assertAlmostEquals(out, manager.ones(new Shape(1, 2, 4)));

                testEncode(manager, block);
            }
        }
    }

    private void testEncode(NDManager manager, Block block) throws IOException {
        PairList<String, Parameter> original = block.getParameters();
        File temp = File.createTempFile("block", ".param");
        DataOutputStream os = new DataOutputStream(Files.newOutputStream(temp.toPath()));
        block.saveParameters(os);
        block.loadParameters(manager, new DataInputStream(Files.newInputStream(temp.toPath())));
        Files.delete(temp.toPath());
        PairList<String, Parameter> loaded = block.getParameters();
        int bound = original.size();
        for (int idx = 0; idx < bound; idx++) {
            Assert.assertEquals(original.valueAt(idx), loaded.valueAt(idx));
        }
    }
}