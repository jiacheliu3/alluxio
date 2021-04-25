package alluxio.stress.worker;

import alluxio.stress.BaseParameters;
import alluxio.stress.GraphGenerator;
import alluxio.stress.Summary;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

public class RpcTaskSummary implements Summary {
  private List<RpcTaskResult.Point> mPoints;
  private List<String> mErrors;
  private BaseParameters mBaseParameters;
  private RpcParameters mParameters;

  /**
   * Used for deserialization.
   * */
  @JsonCreator
  public RpcTaskSummary() {}

  public RpcTaskSummary(RpcTaskResult r) {
    mParameters = r.getParameters();
    mBaseParameters = r.getBaseParameters();
    mErrors = r.getErrors();
    mPoints = r.getPoints();
  }

  @Override
  public GraphGenerator graphGenerator() {
    return null;
  }

  @Override
  public String toString() {
    return String.format("RpcTaskSummary: {Points=%s, Errors=%s}%n",
            mPoints, mErrors);
  }

  /**
   * @return the points recorded
   * */
  public List<RpcTaskResult.Point> getPoints() {
    return mPoints;
  }

  /**
   * @param points the data points
   * */
  public void setPoints(List<RpcTaskResult.Point> points) {
    mPoints = points;
  }

  /**
   * @return the errors recorded
   * */
  public List<String> getErrors() {
    return mErrors;
  }

  /**
   * @param errors the errors
   * */
  public void setErrors(List<String> errors) {
    mErrors = errors;
  }

  /**
   * @return the {@link BaseParameters}
   * */
  public BaseParameters getBaseParameters() {
    return mBaseParameters;
  }

  /**
   * @param baseParameters the {@link BaseParameters}
   * */
  public void setBaseParameters(BaseParameters baseParameters) {
    mBaseParameters = baseParameters;
  }

  /**
   * @return the task specific {@link UfsIOParameters}
   * */
  public RpcParameters getParameters() {
    return mParameters;
  }

  /**
   * @param parameters the {@link UfsIOParameters}
   * */
  public void setParameters(RpcParameters parameters) {
    mParameters = parameters;
  }
}