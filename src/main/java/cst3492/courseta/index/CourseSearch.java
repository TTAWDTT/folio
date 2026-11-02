package cst3492.courseta.index;

import java.util.List;

public interface CourseSearch {
    List<CourseChunk> top(String query, int limit);
    List<CourseChunk> chunks();
    int size();
}
