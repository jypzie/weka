/*
 *    ClusterEvaluation.java
 *    Copyright (C) 1999 Mark Hall
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package weka.clusterers;

import java.util.*;
import java.io.*;
import weka.core.*;

/**
 * Class for evaluating clustering models.
 *
 * @author   Mark Hall (mhall@cs.waikato.ac.nz)
 * @version  1.0 - March 1999 - Initial version (Mark)
 */
public class ClusterEvaluation {


  /**
   * Evaluates a clusterer with the options given in an array of
   * strings. It takes the string indicated by "-t" as training file, the
   * string indicated by "-T" as test file.
   * If the test file is missing, a stratified ten-fold
   * cross-validation is performed (distribution clusterers only).
   * Using "-x" you can change the number of
   * folds to be used, and using "-s" the random seed.
   * If the "-p" flag is set it outputs the classification for
   * each test instance. If you provide the name of an object file using
   * "-l", a clusterer will be loaded from the given file. If you provide the
   * name of an object file using "-d", the clusterer built from the
   * training data will be saved to the given file.
   *
   * @param clusterer machine learning clusterer
   * @param options the array of string containing the options
   * @exception Exception if model could not be evaluated successfully
   * @return a string describing the results 
   */
  public static String evaluateClusterer(Clusterer clusterer,
				       String [] options) throws Exception
  {
    int seed=1, folds=10;
    boolean doXval = false;
    Instances train = null;
    Instances test = null;
    Random random;

    String trainFileName, testFileName, seedString, foldsString,
      objectInputFileName, objectOutputFileName;
    String[] savedOptions = null;
    boolean printClusterAssignments=false;
    ObjectInputStream objectInputStream = null;
    ObjectOutputStream objectOutputStream = null;

    StringBuffer text = new StringBuffer();
    
    try
    {
      // Get basic options (options the same for all clusterers
      printClusterAssignments = Utils.getFlag('p', options);

      objectInputFileName = Utils.getOption('l',options);
      objectOutputFileName = Utils.getOption('d',options);

      trainFileName = Utils.getOption('t', options); 
      testFileName = Utils.getOption('T', options); 

      if (trainFileName.length() == 0)
      {
	if (objectInputFileName.length() == 0)
	  {
	    throw new Exception("No training file and no object "
			      +"input file given.");
	  }
	if (testFileName.length() == 0)
	  {
	    throw new Exception("No training file and no test file given.");
	  }
      }
      else if ((objectInputFileName.length() != 0)
	       && (printClusterAssignments == false))
	{
	  throw new Exception("Can't use both train and model file "
			      +"unless -p specified.");
	}

      seedString = Utils.getOption('s', options);
      if (seedString.length() != 0) 
      {
	seed = Integer.parseInt(seedString);
      }

      foldsString = Utils.getOption('x', options);
      if (foldsString.length() != 0) 
      {
	folds = Integer.parseInt(foldsString);
	doXval = true;
      }      
    }
    catch (Exception e)
    {
       throw new Exception('\n' + e.getMessage()
			   + makeOptionString(clusterer));
    }

    try
      {
	if (trainFileName.length() != 0)
	  {
	    train = 
	      new Instances(new FileReader(trainFileName));
	  }
	if (objectInputFileName.length() != 0)
	  {
	    objectInputStream = 
	      new ObjectInputStream(new FileInputStream(objectInputFileName));
	  }
	if (objectOutputFileName.length() != 0)
	  {
	    objectOutputStream =
	      new ObjectOutputStream(new 
				     FileOutputStream(objectOutputFileName));
	  }
      }
    catch (Exception e) 
      {
	throw new Exception("Can't open file " + e.getMessage() + '.');
      }

    // Save options
    if (options != null) 
    {
      savedOptions = new String[options.length];
      System.arraycopy(options, 0, savedOptions, 0, options.length);
    }
    
    if (objectInputFileName.length() != 0)
      Utils.checkForRemainingOptions(options);

    // Set options for clusterer
    if (clusterer instanceof OptionHandler) 
    {
      ((OptionHandler)clusterer).setOptions(options);
    }
    Utils.checkForRemainingOptions(options);
      
    if (objectInputFileName.length() != 0)
      {
	// Load the clusterer from file
	clusterer = (Clusterer) objectInputStream.readObject();
	objectInputStream.close();
      }
    else
      // Build the clusterer if no object file provided
      clusterer.buildClusterer(train);

    /* Output cluster predictions only (for the test data if specified,
       otherwise for the training data */
    if (printClusterAssignments)
    {
      return printClusterings(clusterer, train, testFileName);
    }
    
    text.append(clusterer.toString());
    
    text.append("\n\n=== Clustering stats for training data ===\n\n"+
		printClusterStats(clusterer, trainFileName));
    if (testFileName.length() != 0)
      text.append("\n\n=== Clustering stats for testing data ===\n\n"+
		printClusterStats(clusterer, testFileName));

    if ((clusterer instanceof DistributionClusterer) &&
	(doXval == true) &&
	(testFileName.length() == 0) &&
	(objectInputFileName.length() == 0))
    {
      // cross validate the log likelihood on the training data
      random = new Random(seed);
      random.setSeed(seed);
      train.randomize(random);
      text.append(crossValidateModel(clusterer.getClass().getName(),
				     train, folds,
				     savedOptions));
    }

    // Save the clusterer if an object output file is provided
    if (objectOutputFileName.length() != 0)
      {
	objectOutputStream.writeObject(clusterer);
	objectOutputStream.flush();
	objectOutputStream.close();
      }

    return text.toString();
  }

  
  /**
   * Performs a cross-validation 
   * for a distribution clusterer on a set of instances.
   *
   * @param clustererString a string naming the class of the clusterer
   * @param data the data on which the cross-validation is to be 
   * performed 
   * @param numFolds the number of folds for the cross-validation
   * @param options the options to the clusterer
   * @return a string containing the cross validated log likelihood
   * @exception Exception if a clusterer could not be generated 
   */
  public static String crossValidateModel(String clustererString,
					  Instances data, int numFolds,
					  String [] options)
    throws Exception
  {
    Clusterer clusterer=null;
    Instances train, test;
    String [] savedOptions = null;
    double foldAv;
    double CvAv = 0.0;
    double [] tempDist;  
    StringBuffer CvString = new StringBuffer();

    if (options != null) 
    {
      savedOptions = new String[options.length];
    }

    data = new Instances(data);

    for (int i=0;i<numFolds;i++)
    {
      // create clusterer
      try 
      {
	clusterer = 
	  (Clusterer)Class.forName(clustererString).
	    newInstance();
      } 
      catch (Exception e) 
      {
	throw new Exception("Can't find class with name " + 
			    clustererString + '.');
      }

      if (!(clusterer instanceof DistributionClusterer))
	throw new Exception(clustererString+" must be a distrinbution "+
			    "clusterer.");

      // Save options
      if (options != null) 
      {
	System.arraycopy(options, 0, savedOptions, 0, 
			 options.length);
      }

      // Parse options
      if (clusterer instanceof OptionHandler)
      {
	try 
	{
	  ((OptionHandler)clusterer).setOptions(savedOptions);
	  Utils.checkForRemainingOptions(savedOptions);
	} 
	catch (Exception e) 
	{
	  throw new Exception("Can't parse given options in " + 
			      "cross-validation!");
	}
      }
      
      // Build and test classifier 
      train = data.trainCV(numFolds, i);
      clusterer.buildClusterer(train);
      test = data.testCV(numFolds,i);
      foldAv = 0.0;     
      for (int j=0;j<test.numInstances();j++)
      {
	tempDist = 
	((DistributionClusterer)
	 clusterer).distributionForInstance(test.instance(j));

	double temp = Utils.sum(tempDist);
	if (temp > 0)
	  foldAv += Math.log(temp);
      }
      CvAv += (foldAv / test.numInstances());
    }
    CvAv /= numFolds; 
    CvString.append("\n"+numFolds+" fold CV Log Likelihood: "+
		    Utils.doubleToString(CvAv,6,4)+"\n");
    return CvString.toString();
  }

  // ===============
  // Private methods
  // ===============

  /**
   * Print the cluster statistics for either the training
   * or the testing data.
   *
   * @param clusterer the clusterer to use for generating statistics.
   * @return a string containing cluster statistics.
   * @exception if statistics can't be generated.
   */
    private static String printClusterStats(Clusterer clusterer,
					  String fileName) throws Exception
    {
      StringBuffer text = new StringBuffer();
      int i=0;
      int cnum;
      double loglk = 0.0;
      double [] dist;
      double temp;
    
      int cc = clusterer.numberOfClusters();
      double [] instanceStats = new double [cc];

      if (fileName.length() != 0) 
      {
	FileReader inStream = null;
	try 
	{
	  inStream = new FileReader(fileName);
	} 
	catch (Exception e) 
	{
	  throw new Exception("Can't open file " + e.getMessage() + '.');
	}
	Instances inst = new Instances(inStream, 1);

	while(inst.readInstance(inStream))
	{
	  try
	  {
	    cnum = clusterer.clusterInstance(inst.instance(0));
	    
	    if (clusterer instanceof DistributionClusterer)
	    {
	      dist = ((DistributionClusterer)clusterer).
	      distributionForInstance(inst.instance(0));

	      temp = Utils.sum(dist);

	      if (temp > 0)
		loglk += Math.log(temp);
	    }
	  }
	  catch (Exception e) 
	  {
	    throw new Exception('\n' + "Unable to cluster instance\n"+
				e.getMessage());
	  }

	  instanceStats[cnum]++;
	  inst.delete(0);
	  i++;
	}
	
	double sum = Utils.sum(instanceStats);
	loglk /= sum;
	text.append("Cluster Instances\n");

	for (i=0;i<cc;i++)
	{
	  text.append(Utils.doubleToString((double)i,8,0)+" "+
		      Utils.doubleToString(instanceStats[i],9,0)+" ("+
		      Utils.doubleToString((instanceStats[i]/sum*100.0),3,0)
		      +"%)\n");
	}
	
	if (clusterer instanceof DistributionClusterer)
	  text.append("\nLog likelihood: "+Utils.doubleToString(loglk,1,5)
		      +"\n");
      }

      return text.toString();
    }

  /**
   * Print the cluster assignments for either the training
   * or the testing data.
   *
   * @param clusterer the clusterer to use for cluster assignments
   * @return a string containing the instance indexes and cluster assigns.
   * @exception if cluster assignments can't be printed
   */
    private static String printClusterings(Clusterer clusterer,
					 Instances train,
					 String testFileName) 
    throws Exception
    {
      StringBuffer text = new StringBuffer();
      int i=0;
      int cnum;
    
      if (testFileName.length() != 0) 
      {
	FileReader testStream = null;
	try 
	{
	  testStream = new FileReader(testFileName);
	} 
	catch (Exception e) 
	{
	  throw new Exception("Can't open file " + e.getMessage() + '.');
	}
	Instances test = new Instances(testStream, 1);

	while(test.readInstance(testStream))
	{
	  try
	  {
	    cnum = clusterer.clusterInstance(test.instance(0));
	  }
	  catch (Exception e) 
	  {
	    throw new Exception('\n' + "Unable to cluster instance\n"+
				e.getMessage());
	  }

	  text.append(i+" "+cnum+"\n");
	  test.delete(0);
	  i++;
	}
      }    
      else // output for training data
      {
	for (i=0;i<train.numInstances();i++)
	{
	  try
	  {
	    cnum = clusterer.clusterInstance(train.instance(i));
	  }
	  catch (Exception e) 
	  {
	    throw new Exception('\n' + "Unable to cluster instance\n"+
				e.getMessage());
	  }

	  text.append(i+" "+cnum+"\n");

	}
      }

      return text.toString();
    }

  /**
   * Make up the help string giving all the command line options
   *
   * @param clusterer the clusterer to include options for
   * @return a string detailing the valid command line options
   */
  private static String makeOptionString(Clusterer clusterer) 
  {

    StringBuffer optionsText = new StringBuffer("");

    // General options
    optionsText.append("\n\nGeneral options:\n\n");
    optionsText.append("-t <name of training file>\n");
    optionsText.append("\tSets training file.\n");
    optionsText.append("-T <name of test file>\n");
    optionsText.append("-l <name of input file>\n");
    optionsText.append("\tSets model input file.\n");
    optionsText.append("-d <name of output file>\n");
    optionsText.append("\tSets model output file.\n");
    optionsText.append("-p\n");
    optionsText.append("\tOutput predictions. Predictions are for training file"
		       +"\n\tif only training file is specified,"
		       +"\n\totherwise predictions are for the test file.\n");
    optionsText.append("-x <number of folds>\n");
    optionsText.append("\tOnly Distribution Clusterers can be cross validated.\n");
    optionsText.append("-s <random number seed>\n");

    // Get scheme-specific options

    if (clusterer instanceof OptionHandler) 
    {
      optionsText.append("\nOptions specific to "
			  + clusterer.getClass().getName()
			  + ":\n\n");

      Enumeration enum = ((OptionHandler)clusterer).listOptions();
      while (enum.hasMoreElements()) 
      {
	Option option = (Option) enum.nextElement();
	optionsText.append(option.synopsis() + '\n');
	optionsText.append(option.description() + "\n");
      }
    }

    return optionsText.toString();
  }

}
