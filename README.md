## Team 17 : CS330 Emerging Systems Project

### Enhancements
> in `CameraScreen.kt`
1. Clear the imageAnalyzer and use a new executor with 2-thread pool.
2. Allocate 1 Classifier that utilizes 1 thread per each thread in the fixed pool.
    - Classifier initializer option : `threadNumber = 1, useGPU = false`
3. 2 Classifiers on 2 CPU threads detects with synchronized `bitmapBuffer`
4. Shutdown new executor after inference

### Result
> Application Record Pictures
<img width="653" alt="Application Record" src="https://github.com/user-attachments/assets/5b8f6285-e7fb-4daf-9fba-eae4ab8930d6" />

--
> Inference Latency Enhancement Chart
<img width="653" alt="Inference Latency" src="https://github.com/user-attachments/assets/d18d5969-8cf1-4f23-8229-750632797b3d" />

The graph compares three scenarios:
* Human Detection executed alone on the GPU (orange).
* Human Detection executed concurrently with the LLM on the GPU (yellow).
* Human Detection executed concurrently with the LLM but offloaded to the CPU with 2 threads (green).

When Human Detection was executed alone on the GPU without the LLM (1, orange), the inference latency consistently stayed below 100ms.
In contrast, when Human Detection was executed alongside the LLM on the GPU (2, yellow), the inference latency spiked significantly as the LLM began generating responses, averaging 700–800ms. This latency also demonstrated periodic fluctuations corresponding to the computational load.

In contrast, our code, which switches to executing Human Detection on the CPU with 2 threads when the inference latency exceeds the threshold 300ms (3, green), demonstrated significant improvements.
Even immediately after the LLM began generating complex responses, the inference latency did not spike.
Although a gradual increase in latency was observed after the 40th inference as the LLM continued its intensive computations, the latency remained comparable to the scenario where Human Detection was executed alone on the GPU without the LLM (1, orange).

The average latency of Human Detection executed on the CPU threads (3) was measured at 179.94ms (Table 1.), which is over four times faster than the average latency of 747.82ms (Table 1.) observed when both tasks were executed concurrently on the GPU (2).
This confirms that our improvement demonstrates a clear enhancement in performance. 

> Table 1. Average Inference Latency

||without LLM|1 GPU thread|2 CPU threads|
|------|---|---|---|
|Average Inference Latency (ms)|93.44|747.82|179.94|

