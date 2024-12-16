package com.smartcloudsolutions.tollingvision;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.protobuf.ByteString;
import com.smartcloudsolutions.tollingvision.TollingVisionServiceGrpc.TollingVisionServiceStub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

public class TollingVisionSample {

    /**
     * Main method to demonstrate how to use the TollingVision gRPC service.
     * 
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 9) {
            System.out.println(
                    "Usage: java com.smartcloudsolutions.tollingvision.TollingVisionSample <service_url> <secured> <max_parallel_requests> <image_folder_path> <csv_file_path> <group_regex> <front_regex> <rear_regex> <overview_regex>");
            System.exit(1);
        }

        long now = System.currentTimeMillis();
        String serviceUrl = args[0];
        boolean secured = Boolean.parseBoolean(args[1]);
        int maxParallelRequests = Integer.parseInt(args[2]);
        String imageFolderPath = args[3];
        String csvFilePath = args[4];
        String groupRegex = args[5];
        String frontRegex = args[6];
        String rearRegex = args[7];
        String overviewRegex = args[8];

        ManagedChannel channel;
        if (secured) {
            channel = NettyChannelBuilder.forTarget(serviceUrl).useTransportSecurity().build();
        } else {
            channel = ManagedChannelBuilder.forTarget(serviceUrl).usePlaintext().build();
        }

        TollingVisionServiceStub stub = TollingVisionServiceGrpc.newStub(channel);

        List<File> imageFiles = listFilesRecursively(new File(imageFolderPath));
        Map<String, List<File>> groupedImages = groupImages(imageFiles, groupRegex);

        System.out.println("Total analyze service calls: " + groupedImages.size());

        CountDownLatch latch = new CountDownLatch(groupedImages.size());
        Semaphore semaphore = new Semaphore(maxParallelRequests);

        try (FileWriter csvWriter = new FileWriter(csvFilePath)) {
            csvWriter.append(
                    "Front Image,Rear Image,Overview Image,Node,Front Plate,Front Plate Alternative,Rear Plate,Rear Plate Alternative,MMR,MMR Alternative\n");

            for (Map.Entry<String, List<File>> entry : groupedImages.entrySet()) {
                semaphore.acquire();

                String group = entry.getKey();
                List<File> files = entry.getValue();

                long nowGroup = System.currentTimeMillis();
                System.out.println("Processing group: " + group);

                EventRequest eventRequest = createEventRequest(files, frontRegex, rearRegex, overviewRegex);
                if (eventRequest == null) {
                    latch.countDown();
                    semaphore.release();
                    continue;
                }

                stub.analyze(eventRequest, new StreamObserver<EventResponse>() {
                    @Override
                    public void onNext(EventResponse response) {
                        int requestCount = eventRequest.getFrontImageCount() + eventRequest.getRearImageCount()
                                + eventRequest.getOverviewImageCount();
                        if (response.hasEventResult()) {
                            EventResult result = response.getEventResult();
                            try {
                                List<File> overviewFilesList = files.stream()
                                        .filter(f -> f.getName().matches(overviewRegex))
                                        .collect(Collectors.toList());
                                List<File> frontFilesList = files.stream()
                                        .filter(f -> f.getName().matches(frontRegex)
                                                && !overviewFilesList.contains(f))
                                        .collect(Collectors.toList());
                                List<File> rearFilesList = files.stream()
                                        .filter(f -> f.getName().matches(rearRegex)
                                                && !overviewFilesList.contains(f))
                                        .collect(Collectors.toList());

                                String frontFiles = frontFilesList.stream().map(File::getName)
                                        .collect(Collectors.joining("|"));
                                String rearFiles = rearFilesList.stream().map(File::getName)
                                        .collect(Collectors.joining("|"));
                                String overviewFiles = overviewFilesList.stream().map(File::getName)
                                        .collect(Collectors.joining("|"));

                                String frontPlate = result.hasFrontPlate() ? formatPlate(result.getFrontPlate())
                                        : "";
                                String frontPlateAlt = result.getFrontPlateAlternativeList().stream()
                                        .map(TollingVisionSample::formatPlate).collect(Collectors.joining("|"));
                                String rearPlate = result.hasRearPlate() ? formatPlate(result.getRearPlate()) : "";
                                String rearPlateAlt = result.getRearPlateAlternativeList().stream()
                                        .map(TollingVisionSample::formatPlate).collect(Collectors.joining("|"));
                                String mmr = result.hasMmr() ? formatMmr(result.getMmr()) : "";
                                String mmrAlt = result.getMmrAlternativeList().stream()
                                        .map(TollingVisionSample::formatMmr).collect(Collectors.joining("|"));

                                csvWriter.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                                        frontFiles, rearFiles, overviewFiles, result.getNode(),
                                        frontPlate, frontPlateAlt, rearPlate, rearPlateAlt, mmr, mmrAlt));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (response.hasPartialResult()) {
                            PartialResult partialResult = response.getPartialResult();
                            if (partialResult.hasError()) {
                                ErrorResponse error = partialResult.getError();
                                System.out.println((partialResult.getResultIndex() + 1) + "/" + requestCount
                                        + ". Partial result (Error): " + error);
                            } else if (partialResult.hasResult()
                                    && partialResult.getResult().getStatus() == Status.RESULT) {
                                /*
                                 * SearchResponse searchResponse = partialResult.getResult();
                                 * System.out.println((partialResult.getResultIndex() + 1) + "/" + requestCount
                                 * + ". Partial result (SearchResponse): " + searchResponse);
                                 */

                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                        latch.countDown();
                        semaphore.release();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                        semaphore.release();
                        System.out.println(group + " time: " + (System.currentTimeMillis() - nowGroup) + " ms");
                    }
                });
            }
            latch.await();
        } finally {
            channel.shutdown();
        }
        System.out.println("Total time: " + (System.currentTimeMillis() - now) + " ms");
    }

    private static List<File> listFilesRecursively(File folder) {
        List<File> fileList = new ArrayList<>();
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        fileList.addAll(listFilesRecursively(file));
                    } else if (file.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|bmp)$")) {
                        fileList.add(file);
                    }
                }
            }
        }
        return fileList;
    }

    private static Map<String, List<File>> groupImages(List<File> files, String groupRegex) {
        Pattern groupPattern = Pattern.compile(groupRegex);

        return files.stream().collect(Collectors.groupingBy(file -> {
            Matcher matcher = groupPattern.matcher(file.getName());
            return matcher.find() ? matcher.group() : "ungrouped";
        }));
    }

    private static EventRequest createEventRequest(List<File> files, String frontRegex, String rearRegex,
            String overviewRegex) {
        Pattern frontPattern = Pattern.compile(frontRegex);
        Pattern rearPattern = Pattern.compile(rearRegex);
        Pattern overviewPattern = Pattern.compile(overviewRegex);

        List<Image> frontImages = new ArrayList<>();
        List<Image> rearImages = new ArrayList<>();
        List<Image> overviewImages = new ArrayList<>();

        for (File file : files) {
            byte[] imageData;
            try {
                imageData = Files.readAllBytes(Paths.get(file.getPath()));
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            Image img = Image.newBuilder().setData(ByteString.copyFrom(imageData)).build();

            if (overviewPattern.matcher(file.getName()).find()) {
                overviewImages.add(img);
            } else if (frontPattern.matcher(file.getName()).find()) {
                frontImages.add(img);
            } else if (rearPattern.matcher(file.getName()).find()) {
                rearImages.add(img);
            }
        }

        if (frontImages.isEmpty() && rearImages.isEmpty() && overviewImages.isEmpty()) {
            return null;
        }

        return EventRequest.newBuilder()
                .addAllFrontImage(frontImages)
                .addAllRearImage(rearImages)
                .addAllOverviewImage(overviewImages)
                .build();
    }

    private static String formatPlate(Plate plate) {
        return String.format("%s %s %s %s %d%% (text: %d%%|state: %d%%)", plate.getText(), plate.getCountry(),
                plate.getState(), plate.getCategory(), plate.getConfidence(), plate.getTextConfidence(),
                plate.getPlateTypeConfidence());
    }

    private static String formatMmr(Mmr mmr) {
        return String.format("%s %s (%s %s %s %s)", mmr.getMake(), mmr.getModel(), mmr.getGeneration(),
                mmr.getCategory(), mmr.getBodyType(), mmr.getViewPoint(), mmr.getColorName());
    }
}
