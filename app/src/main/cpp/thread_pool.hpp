#pragma once

#include <algorithm>
#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <functional>
#include <latch>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

namespace commix {

/**
 * Persistent C++20 thread pool with RAII join-on-destruct and std::latch
 * synchronization for zero-spawn-overhead parallel pixel processing.
 */
class ThreadPool {
public:
    static ThreadPool& instance() noexcept {
        static ThreadPool s_pool(defaultWorkerCount());
        return s_pool;
    }

    explicit ThreadPool(size_t numThreads = 4) noexcept : m_stop(false) {
        if (numThreads < 1) numThreads = 1;
        m_workers.reserve(numThreads);
        for (size_t i = 0; i < numThreads; ++i) {
            m_workers.emplace_back([this]() { workerLoop(); });
        }
    }

    ~ThreadPool() noexcept {
        shutdown();
    }

    ThreadPool(const ThreadPool&) = delete;
    ThreadPool& operator=(const ThreadPool&) = delete;

    /**
     * Enqueues a task onto the worker pool.
     * Returns true if queued, or false if the pool has been shut down
     * (in which case callers must execute inline or account for completion).
     */
    bool enqueue(std::function<void()> task) noexcept {
        if (!task) return false;
        {
            std::unique_lock<std::mutex> lock(m_queueMutex);
            if (m_stop) return false;
            m_tasks.push(std::move(task));
        }
        m_condition.notify_one();
        return true;
    }

    /**
     * Splits work range [0, count) evenly across worker threads and blocks until
     * every slice completes. Any slice rejected after shutdown runs inline on the
     * calling thread, guaranteeing zero hangs and zero lost work.
     */
    template <typename Func>
    void parallelFor(int count, Func&& fn, int minPerSlice = 16) noexcept {
        if (count <= 0) return;
        const size_t workers = m_workers.size();
        if (workers <= 1 || count <= minPerSlice || isStopped()) {
            fn(0, count);
            return;
        }

        const int maxSlices = std::max(1, count / std::max(1, minPerSlice));
        const int numSlices = std::min(static_cast<int>(workers), maxSlices);
        if (numSlices <= 1) {
            fn(0, count);
            return;
        }

        const int sliceSize = (count + numSlices - 1) / numSlices;
        // Run the last slice on the caller thread to avoid idle waiting + save one wake.
        const int bgSlices = numSlices - 1;
        std::latch done(bgSlices);

        for (int s = 0; s < bgSlices; ++s) {
            const int start = s * sliceSize;
            const int end = std::min(start + sliceSize, count);
            if (start >= end) {
                done.count_down();
                continue;
            }
            const bool queued = enqueue([&, start, end]() {
                fn(start, end);
                done.count_down();
            });
            if (!queued) {
                fn(start, end);
                done.count_down();
            }
        }

        const int lastStart = bgSlices * sliceSize;
        if (lastStart < count) {
            fn(lastStart, count);
        }

        done.wait();
    }

    size_t workerCount() const noexcept {
        return m_workers.size();
    }

    bool isStopped() const noexcept {
        std::unique_lock<std::mutex> lock(m_queueMutex);
        return m_stop;
    }

    void shutdown() noexcept {
        {
            std::unique_lock<std::mutex> lock(m_queueMutex);
            if (m_stop) return;
            m_stop = true;
        }
        m_condition.notify_all();
        // Join all worker threads cleanly; workerLoop drains remaining tasks first.
        for (auto& worker : m_workers) {
            if (worker.joinable()) {
                worker.join();
            }
        }
    }

private:
    static size_t defaultWorkerCount() noexcept {
        const unsigned int hw = std::thread::hardware_concurrency();
        if (hw == 0) return 4;
        return static_cast<size_t>(std::clamp(hw, 4u, 8u));
    }

    void workerLoop() noexcept {
        while (true) {
            std::function<void()> task;
            {
                std::unique_lock<std::mutex> lock(m_queueMutex);
                m_condition.wait(lock, [this]() {
                    return m_stop || !m_tasks.empty();
                });
                if (m_stop && m_tasks.empty()) {
                    return;
                }
                if (m_tasks.empty()) {
                    continue;
                }
                task = std::move(m_tasks.front());
                m_tasks.pop();
            }
            if (task) {
                task();
            }
        }
    }

    std::vector<std::thread> m_workers;
    std::queue<std::function<void()>> m_tasks;
    mutable std::mutex m_queueMutex;
    std::condition_variable m_condition;
    bool m_stop;
};

} // namespace commix
